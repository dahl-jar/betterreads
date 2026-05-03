#!/usr/bin/env bash
#
# Pulls an encrypted Postgres dump from Cloudflare R2 and restores it.
# Used for disaster recovery and, more often, for the quarterly restore drill.
#
# Reads its config from the same env file as pg-backup.sh, plus an OBJECT_NAME
# that points at the specific backup to restore. The runbook at
# docs/runbooks/postgres-backup.md walks through both the drill and the real
# disaster path step by step.
#
# Refuses to write into the live "betterreads" database. If you really mean to
# overwrite production, restore into a throwaway and rename. Belt and braces.

set -euo pipefail

require() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "missing required env: $name" >&2
        exit 64
    fi
}

require OBJECT_NAME
require PG_CONTAINER
require DB_NAME
require DB_USERNAME
require BACKUP_GPG_PASSPHRASE
require R2_ACCESS_KEY_ID
require R2_SECRET_ACCESS_KEY
require R2_BUCKET
require R2_ENDPOINT

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT"
export RCLONE_CONFIG_R2_ACL=private
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true

if [[ "$DB_NAME" == "betterreads" ]]; then
    echo "refusing to restore into the live 'betterreads' database. Restore into a throwaway DB and rename." >&2
    exit 66
fi

echo "restore.start object=${OBJECT_NAME} target=${DB_NAME}" >&2

rclone cat "r2:${R2_BUCKET}/${OBJECT_NAME}" \
    | gpg --batch --yes --quiet --decrypt --passphrase-fd 3 3<<<"$BACKUP_GPG_PASSPHRASE" \
    | gunzip \
    | docker exec -i "$PG_CONTAINER" \
        psql --username="$DB_USERNAME" --dbname="$DB_NAME" --set ON_ERROR_STOP=1

echo "restore.ok object=${OBJECT_NAME} target=${DB_NAME}" >&2
