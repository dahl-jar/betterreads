#!/bin/bash
# Pull-based deploy script. Runs on the BetterReads VM via systemd timer (60s).
#
# What it does:
#   1. Acquire a flock so concurrent timer fires do not race
#   2. Read GITHUB_TOKEN_DEPLOY from /opt/betterreads/.env
#   3. Read /etc/betterreads/version.lock if present (pin to a SHA), else "latest"
#   4. Query GitHub releases API for the target tag
#   5. Compare release SHA against /opt/betterreads/deploy/installed.sha; exit if same
#   6. Download jar + .sha256 + .minisig to a temp dir
#   7. Sanity-check size, verify SHA256, verify minisign signature
#   8. Atomically swap app.jar (keeping the old one as app.jar.previous)
#   9. systemctl restart betterreads
#  10. Poll /healthz for up to 90s; rollback on failure
#  11. Record installed SHA on success
#
# Designed to be safe to run repeatedly. The cheap path (no new release) is
# one HTTP call and a file compare.

set -euo pipefail

# --- Configuration ---
REPO="dahl-jar/betterreads"
APP_DIR="/opt/betterreads"
DEPLOY_DIR="${APP_DIR}/deploy"
ENV_FILE="${APP_DIR}/.env"
APP_JAR="${APP_DIR}/app.jar"
APP_JAR_PREVIOUS="${APP_DIR}/app.jar.previous"
INSTALLED_SHA_FILE="${DEPLOY_DIR}/installed.sha"
FAILED_SHAS_FILE="${DEPLOY_DIR}/failed.shas"
PUBKEY="${DEPLOY_DIR}/pubkey.minisig.pub"
VERSION_LOCK="/etc/betterreads/version.lock"
LOCK_FILE="/run/betterreads-deploy.lock"
HEALTH_URL="http://127.0.0.1:8080/healthz"
HEALTH_TIMEOUT_SEC=90
HEALTH_POLL_INTERVAL_SEC=2
PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-https://api.betterreadsapp.com/healthz}"
PUBLIC_HEALTH_TIMEOUT_SEC=60
SERVICE_NAME="betterreads"
JAR_MIN_SIZE=10485760    # 10 MB sanity floor
JAR_MAX_SIZE=209715200   # 200 MB sanity ceiling
FAILED_JAR_MAX_AGE_DAYS=14
FAILED_SHAS_MAX_AGE_DAYS=30

# --- Logging helpers ---
log() { logger -t betterreads-deploy -p user.info -- "$*"; echo "$(date -u +%FT%TZ) $*"; }
err() { logger -t betterreads-deploy -p user.err  -- "$*"; echo "$(date -u +%FT%TZ) ERROR $*" >&2; }

# --- Concurrency lock ---
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "another deploy run is in progress; exiting"
  exit 0
fi

# Heartbeat so the Loki {job="bd-deploy"} stream always has a recent entry. Without this the
# stream is empty 99% of the time and looks broken in dashboards. Anything past this line is
# either a no-op exit (no new release) or a real deploy with its own log lines.
log "deploy-pull tick"

# --- Load PAT ---
if [ ! -f "$ENV_FILE" ]; then
  err "env file not found: $ENV_FILE"
  exit 1
fi
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a
if [ -z "${GITHUB_TOKEN_DEPLOY:-}" ]; then
  err "GITHUB_TOKEN_DEPLOY not set in $ENV_FILE"
  exit 1
fi

# --- Resolve target SHA ---
if [ -s "$VERSION_LOCK" ]; then
  target_sha=$(tr -d '[:space:]' < "$VERSION_LOCK")
  log "version lock active: pinned to $target_sha"
else
  target_sha=""
fi

# --- Fetch release metadata ---
api_base="https://api.github.com/repos/${REPO}"
response_body=$(mktemp)
trap 'rm -f "$response_body"' EXIT

# Captures HTTP status separately from the body so the caller can branch on the actual code.
# A bare `curl --fail || ...` collapses every error (401, 403, 5xx, DNS, timeout) to "no release",
# which silently disables the deploy timer when only an actual 404 should be a clean no-op.
api_call() {
  curl -sS \
    --max-time 30 \
    -o "$response_body" \
    -w '%{http_code}' \
    -H "Authorization: Bearer ${GITHUB_TOKEN_DEPLOY}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@"
}

if [ -n "$target_sha" ]; then
  http_status=$(api_call "${api_base}/releases/tags/deploy-${target_sha}") || {
    err "GitHub API call failed (network or curl error) for pinned tag deploy-${target_sha}"
    exit 1
  }
  if [ "$http_status" != "200" ]; then
    err "GitHub API returned ${http_status} for pinned tag deploy-${target_sha}"
    exit 1
  fi
else
  http_status=$(api_call "${api_base}/releases/latest") || {
    err "GitHub API call failed (network or curl error) on releases/latest"
    exit 1
  }
  case "$http_status" in
    200)
      ;;
    404)
      log "no releases yet; nothing to deploy"
      exit 0
      ;;
    *)
      err "GitHub API returned ${http_status} on releases/latest"
      exit 1
      ;;
  esac
fi
release_json=$(cat "$response_body")

# Tags are "deploy-<sha>"; strip the prefix to recover the bare SHA used in
# asset filenames and recorded in installed.sha.
release_tag=$(echo "$release_json" | jq -r '.tag_name // empty')
if [ -z "$release_tag" ]; then
  err "could not extract tag from release JSON"
  exit 1
fi
release_sha="${release_tag#deploy-}"
if [ "$release_sha" = "$release_tag" ]; then
  err "release tag '$release_tag' does not have expected 'deploy-' prefix"
  exit 1
fi

# --- Already installed? ---
if [ -s "$INSTALLED_SHA_FILE" ]; then
  installed_sha=$(tr -d '[:space:]' < "$INSTALLED_SHA_FILE")
  if [ "$installed_sha" = "$release_sha" ]; then
    # Cheap exit path. No log to keep the journal quiet on the common case.
    exit 0
  fi
fi

# --- Has this SHA already failed once? ---
# Once a SHA has failed healthcheck, it is recorded in $FAILED_SHAS_FILE so the
# puller does not loop redeploying the same broken release every 60s. Recovery
# requires a fresh release with a different SHA, or operator action: removing
# the line from failed.shas, or pinning a known-good SHA in version.lock.
if [ -s "$FAILED_SHAS_FILE" ] && awk -v sha="$release_sha" '$1 == sha { found = 1 } END { exit !found }' "$FAILED_SHAS_FILE"; then
  # Log once an hour so the operator notices, but stay quiet otherwise.
  marker="${DEPLOY_DIR}/.skipped-${release_sha}"
  if [ ! -f "$marker" ] || [ "$(find "$marker" -mmin +60 2>/dev/null)" ]; then
    err "skipping release ${release_sha}: previously failed healthcheck. Remove from ${FAILED_SHAS_FILE} or pin a different SHA in ${VERSION_LOCK} to retry."
    touch "$marker"
  fi
  exit 0
fi

if [ -s "$INSTALLED_SHA_FILE" ]; then
  log "new release available: $(tr -d '[:space:]' < "$INSTALLED_SHA_FILE") -> $release_sha"
else
  log "no prior installed SHA; deploying $release_sha"
fi

# --- Resolve asset URLs ---
jar_url=$(echo "$release_json" | jq -r --arg name "betterreads-${release_sha}.jar"           '.assets[] | select(.name == $name) | .url')
sha_url=$(echo "$release_json" | jq -r --arg name "betterreads-${release_sha}.jar.sha256"    '.assets[] | select(.name == $name) | .url')
sig_url=$(echo "$release_json" | jq -r --arg name "betterreads-${release_sha}.jar.minisig"   '.assets[] | select(.name == $name) | .url')

for var_name in jar_url sha_url sig_url; do
  if [ -z "${!var_name}" ] || [ "${!var_name}" = "null" ]; then
    err "release ${release_sha} is missing asset (${var_name})"
    exit 1
  fi
done

# --- Download to temp dir ---
work_dir=$(mktemp -d -t betterreads-deploy-XXXXXX)
trap 'rm -rf "$work_dir"' EXIT

download() {
  local url="$1" out="$2"
  curl -sS \
    --fail \
    --location \
    --max-time 120 \
    -H "Authorization: Bearer ${GITHUB_TOKEN_DEPLOY}" \
    -H "Accept: application/octet-stream" \
    -o "$out" \
    "$url"
}

log "downloading release assets"
download "$jar_url" "${work_dir}/app.jar"
download "$sha_url" "${work_dir}/app.jar.sha256"
download "$sig_url" "${work_dir}/app.jar.minisig"

# --- Sanity: jar size ---
jar_size=$(stat -c%s "${work_dir}/app.jar")
if [ "$jar_size" -lt "$JAR_MIN_SIZE" ] || [ "$jar_size" -gt "$JAR_MAX_SIZE" ]; then
  err "jar size $jar_size out of expected range [$JAR_MIN_SIZE, $JAR_MAX_SIZE]"
  exit 1
fi

# --- Verify SHA256 ---
log "verifying SHA256"
expected_sha=$(awk '{print $1}' < "${work_dir}/app.jar.sha256")
actual_sha=$(sha256sum "${work_dir}/app.jar" | awk '{print $1}')
if [ "$expected_sha" != "$actual_sha" ]; then
  err "SHA256 mismatch: expected $expected_sha, got $actual_sha"
  exit 1
fi

# --- Verify minisign signature ---
log "verifying minisign signature"
if ! minisign -V \
    -p "$PUBKEY" \
    -x "${work_dir}/app.jar.minisig" \
    -m "${work_dir}/app.jar" >/dev/null 2>&1; then
  err "minisign signature verification FAILED for ${release_sha}; refusing to deploy"
  exit 1
fi

# --- Atomic swap ---
log "swapping jar"
chown root:root "${work_dir}/app.jar"
chmod 644 "${work_dir}/app.jar"

if [ -f "$APP_JAR" ]; then
  mv "$APP_JAR" "$APP_JAR_PREVIOUS"
fi
mv "${work_dir}/app.jar" "$APP_JAR"

# --- Restart and healthcheck ---
log "restarting ${SERVICE_NAME}"
systemctl restart "$SERVICE_NAME"

deadline=$(( $(date +%s) + HEALTH_TIMEOUT_SEC ))
healthy=0
while [ "$(date +%s)" -lt "$deadline" ]; do
  body=$(curl -sS --max-time 5 "$HEALTH_URL" 2>/dev/null || true)
  if [ "$body" = "ok" ]; then
    healthy=1
    break
  fi
  sleep "$HEALTH_POLL_INTERVAL_SEC"
done

rollback_and_exit() {
  local reason="$1"
  err "${reason}; rolling back ${release_sha}"
  if [ -f "$APP_JAR_PREVIOUS" ]; then
    mv "$APP_JAR" "${APP_DIR}/app.jar.failed-${release_sha}"
    mv "$APP_JAR_PREVIOUS" "$APP_JAR"
    systemctl restart "$SERVICE_NAME"
    err "rolled back to previous jar"
  else
    err "no previous jar to roll back to; service is in a bad state"
  fi
  # Record the failed SHA so the next timer fire does not redeploy the same bad
  # jar in a loop. Recovery is operator-driven: ship a different SHA, remove
  # the line from $FAILED_SHAS_FILE, or pin a known-good SHA in $VERSION_LOCK.
  echo "$release_sha $(date -u +%FT%TZ)" >> "$FAILED_SHAS_FILE"
  exit 1
}

if [ "$healthy" -ne 1 ]; then
  rollback_and_exit "localhost healthcheck failed after ${HEALTH_TIMEOUT_SEC}s"
fi

# --- Public-URL healthcheck ---
# Localhost-green / public-red is a real failure mode: the app is fine but the
# tunnel or Cloudflare edge is not forwarding traffic. A previous incident left
# api.betterreadsapp.com returning 502 for an unknown amount of time while the
# localhost check stayed green. Treat the public route as part of the deploy
# contract: if it isn't serving 200 within the window, roll back.
log "verifying public route ${PUBLIC_HEALTH_URL}"
public_deadline=$(( $(date +%s) + PUBLIC_HEALTH_TIMEOUT_SEC ))
public_healthy=0
while [ "$(date +%s)" -lt "$public_deadline" ]; do
  public_status=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' "$PUBLIC_HEALTH_URL" 2>/dev/null || true)
  [ -n "$public_status" ] || public_status="000"
  if [ "$public_status" = "200" ]; then
    public_healthy=1
    break
  fi
  sleep "$HEALTH_POLL_INTERVAL_SEC"
done

if [ "$public_healthy" -ne 1 ]; then
  rollback_and_exit "public healthcheck failed after ${PUBLIC_HEALTH_TIMEOUT_SEC}s (last status ${public_status})"
fi

# --- Success ---
echo "$release_sha" > "$INSTALLED_SHA_FILE"
log "deploy succeeded: ${release_sha}"

# --- Bounded cleanup of past-failure artifacts ---
# Runs only on the success path so cleanup never competes with a struggling
# deploy. Three categories of debris accumulate over time:
#   1. app.jar.failed-<sha>     forensic jars from previous rollbacks
#   2. failed.shas              blocklist of SHAs that previously failed
#   3. .skipped-<sha>           throttle markers for once-per-hour skip logs
# Old entries fade after FAILED_JAR_MAX_AGE_DAYS / FAILED_SHAS_MAX_AGE_DAYS
# so disk usage stays bounded even after months of churn.
cleanup_old_artifacts() {
  # Delete forensic jars older than the retention window.
  find "$APP_DIR" -maxdepth 1 -type f -name 'app.jar.failed-*' \
       -mtime "+${FAILED_JAR_MAX_AGE_DAYS}" -delete 2>/dev/null || true

  # Prune failed.shas lines older than the retention window. The file format
  # is "<sha> <iso8601>". The cutoff is rendered in Python (portable between
  # GNU date on Linux and BSD date on macOS, both of which lack a uniform
  # epoch-to-iso8601 helper). Awk then keeps lines whose ISO 8601 timestamp
  # sorts lexically >= the cutoff; lexical comparison is correct for the
  # fixed-format YYYY-MM-DDTHH:MM:SSZ strings emitted by `date -u +%FT%TZ`.
  if [ -s "$FAILED_SHAS_FILE" ]; then
    cutoff=$(python3 -c "
import datetime, sys
days = int(sys.argv[1])
print((datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=days)).strftime('%Y-%m-%dT%H:%M:%SZ'))
" "$FAILED_SHAS_MAX_AGE_DAYS")
    tmp_failed=$(mktemp)
    awk -v cutoff="$cutoff" '$2 >= cutoff' "$FAILED_SHAS_FILE" > "$tmp_failed"
    if ! cmp -s "$FAILED_SHAS_FILE" "$tmp_failed"; then
      mv "$tmp_failed" "$FAILED_SHAS_FILE"
    else
      rm -f "$tmp_failed"
    fi
  fi

  # Drop skip-throttle markers for SHAs no longer on the blocklist.
  for marker in "${DEPLOY_DIR}"/.skipped-*; do
    [ -e "$marker" ] || continue
    sha=${marker##*/.skipped-}
    if [ ! -s "$FAILED_SHAS_FILE" ] || \
       ! awk -v sha="$sha" '$1 == sha { found = 1 } END { exit !found }' "$FAILED_SHAS_FILE"; then
      rm -f "$marker"
    fi
  done
}

cleanup_old_artifacts
