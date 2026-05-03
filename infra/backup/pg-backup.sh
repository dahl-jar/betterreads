#!/usr/bin/env bash
#
# Streams a Postgres dump to Cloudflare R2, encrypted with gpg.
# Run from cron; on failure cron mails whatever was captured on stderr.
#
# Reads its config from env vars sourced from /opt/betterreads/.env (the same
# file Spring Boot reads, plus the R2 and backup-specific vars). The runbook
# at docs/runbooks/postgres-backup.md lists every var and its meaning.
# Anything missing aborts before pg_dump opens.

set -euo pipefail

require() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "missing required env: $name" >&2
        exit 64
    fi
}

require PG_CONTAINER
require DB_NAME
require DB_USERNAME
require BACKUP_GPG_PASSPHRASE
require R2_ACCESS_KEY_ID
require R2_SECRET_ACCESS_KEY
require R2_BUCKET
require R2_ENDPOINT

OBJECT_PREFIX="${OBJECT_PREFIX:-betterreads/postgres}"

# rclone reads its remote config from RCLONE_CONFIG_* env vars at runtime.
# Older rclone packages don't expand ${VAR} inside config files, so the env-var
# form is more portable. The credentials live only in /opt/betterreads/.env;
# nothing is written to a config file on disk.
export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT"
export RCLONE_CONFIG_R2_ACL=private
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true

date_stamp="$(date -u +%Y-%m-%d)"
week_stamp="$(date -u +%G-W%V)"
object_name_daily="${OBJECT_PREFIX}/daily/pg-${DB_NAME}-${date_stamp}.sql.gz.gpg"
object_name_weekly="${OBJECT_PREFIX}/weekly/pg-${DB_NAME}-${week_stamp}.sql.gz.gpg"

upload_one() {
    local target="$1"
    echo "backup.start target=${target}" >&2

    docker exec -i "$PG_CONTAINER" \
        pg_dump --username="$DB_USERNAME" --no-owner --no-privileges "$DB_NAME" \
        | gzip -9 \
        | gpg --batch --yes --quiet --symmetric --cipher-algo AES256 \
              --passphrase-fd 3 3<<<"$BACKUP_GPG_PASSPHRASE" \
        | rclone rcat "r2:${R2_BUCKET}/${target}"

    local size
    size=$(rclone size --json "r2:${R2_BUCKET}/${target}" 2>/dev/null \
        | grep -o '"bytes":[0-9]*' | head -1 | cut -d: -f2 || echo "0")

    if [[ "$size" -lt 1024 ]]; then
        echo "backup.fail target=${target} reason=upload-too-small size=${size}" >&2
        exit 65
    fi

    echo "backup.ok target=${target} size_bytes=${size}" >&2
}

upload_one "$object_name_daily"

if [[ "$(date -u +%u)" == "7" ]]; then
    upload_one "$object_name_weekly"
fi

echo "backup.done date=${date_stamp}" >&2
