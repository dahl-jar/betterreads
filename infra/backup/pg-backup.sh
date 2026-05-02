#!/usr/bin/env bash
#
# Streams a Postgres dump to OCI Object Storage, encrypted with gpg.
# Run from cron; on failure cron mails whatever was captured on stderr.
#
# Reads its config from env vars sourced from /etc/default/betterreads-backup.
# The runbook at docs/runbooks/postgres-backup.md lists every var and its meaning.
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
require PG_DATABASE
require PG_USER
require GPG_PASSPHRASE
require OCI_BUCKET
require OCI_NAMESPACE

OBJECT_PREFIX="${OBJECT_PREFIX:-betterreads/postgres}"

if [[ -n "${AUTH_FLAG:-}" ]]; then
    read -r -a auth_args <<<"$AUTH_FLAG"
else
    auth_args=(--auth instance_principal)
fi

date_stamp="$(date -u +%Y-%m-%d)"
week_stamp="$(date -u +%G-W%V)"
object_name_daily="${OBJECT_PREFIX}/daily/pg-${PG_DATABASE}-${date_stamp}.sql.gz.gpg"
object_name_weekly="${OBJECT_PREFIX}/weekly/pg-${PG_DATABASE}-${week_stamp}.sql.gz.gpg"

upload_one() {
    local target="$1"
    echo "backup.start target=${target}" >&2

    docker exec -i "$PG_CONTAINER" \
        pg_dump --username="$PG_USER" --no-owner --no-privileges "$PG_DATABASE" \
        | gzip -9 \
        | gpg --batch --yes --quiet --symmetric --cipher-algo AES256 --passphrase-fd 3 3<<<"$GPG_PASSPHRASE" \
        | oci os object put "${auth_args[@]}" \
              --namespace "$OCI_NAMESPACE" \
              --bucket-name "$OCI_BUCKET" \
              --name "$target" \
              --content-type "application/octet-stream" \
              --file - \
              --force

    local size
    size=$(oci os object head "${auth_args[@]}" \
        --namespace "$OCI_NAMESPACE" \
        --bucket-name "$OCI_BUCKET" \
        --name "$target" \
        --query 'data."content-length"' --raw-output 2>/dev/null || echo "0")

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
