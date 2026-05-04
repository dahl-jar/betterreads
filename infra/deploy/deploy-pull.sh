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
PUBKEY="${DEPLOY_DIR}/pubkey.minisig.pub"
VERSION_LOCK="/etc/betterreads/version.lock"
LOCK_FILE="/run/betterreads-deploy.lock"
HEALTH_URL="http://127.0.0.1:8080/healthz"
HEALTH_TIMEOUT_SEC=90
HEALTH_POLL_INTERVAL_SEC=2
SERVICE_NAME="betterreads"
JAR_MIN_SIZE=10485760    # 10 MB sanity floor
JAR_MAX_SIZE=209715200   # 200 MB sanity ceiling

# --- Logging helpers ---
log() { logger -t betterreads-deploy -p user.info -- "$*"; echo "$(date -u +%FT%TZ) $*"; }
err() { logger -t betterreads-deploy -p user.err  -- "$*"; echo "$(date -u +%FT%TZ) ERROR $*" >&2; }

# --- Concurrency lock ---
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "another deploy run is in progress; exiting"
  exit 0
fi

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
api_call() {
  curl -sS \
    --fail \
    --max-time 30 \
    -H "Authorization: Bearer ${GITHUB_TOKEN_DEPLOY}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@"
}

if [ -n "$target_sha" ]; then
  release_json=$(api_call "${api_base}/releases/tags/${target_sha}") || {
    err "no release found for pinned tag ${target_sha}"
    exit 1
  }
else
  release_json=$(api_call "${api_base}/releases/latest") || {
    log "no releases yet; nothing to deploy"
    exit 0
  }
fi

release_sha=$(echo "$release_json" | jq -r '.tag_name // empty')
if [ -z "$release_sha" ]; then
  err "could not extract tag from release JSON"
  exit 1
fi

# --- Already installed? ---
if [ -s "$INSTALLED_SHA_FILE" ]; then
  installed_sha=$(tr -d '[:space:]' < "$INSTALLED_SHA_FILE")
  if [ "$installed_sha" = "$release_sha" ]; then
    # Cheap exit path. No log to keep the journal quiet on the common case.
    exit 0
  fi
  log "new release available: $installed_sha -> $release_sha"
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

if [ "$healthy" -ne 1 ]; then
  err "healthcheck failed after ${HEALTH_TIMEOUT_SEC}s for ${release_sha}; rolling back"
  if [ -f "$APP_JAR_PREVIOUS" ]; then
    mv "$APP_JAR" "${APP_DIR}/app.jar.failed-${release_sha}"
    mv "$APP_JAR_PREVIOUS" "$APP_JAR"
    systemctl restart "$SERVICE_NAME"
    err "rolled back to previous jar"
  else
    err "no previous jar to roll back to; service is in a bad state"
  fi
  exit 1
fi

# --- Success ---
echo "$release_sha" > "$INSTALLED_SHA_FILE"
log "deploy succeeded: ${release_sha}"
