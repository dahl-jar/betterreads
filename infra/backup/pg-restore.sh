#!/usr/bin/env bash
#
# Pulls an encrypted Postgres dump from OCI Object Storage and restores it.
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
require PG_DATABASE
require PG_USER
require GPG_PASSPHRASE
require OCI_BUCKET
require OCI_NAMESPACE

if [[ -n "${AUTH_FLAG:-}" ]]; then
    read -r -a auth_args <<<"$AUTH_FLAG"
else
    auth_args=(--auth instance_principal)
fi

if [[ "$PG_DATABASE" == "betterreads" ]]; then
    echo "refusing to restore into the live 'betterreads' database. Restore into a throwaway DB and rename." >&2
    exit 66
fi

echo "restore.start object=${OBJECT_NAME} target=${PG_DATABASE}" >&2

oci os object get "${auth_args[@]}" \
    --namespace "$OCI_NAMESPACE" \
    --bucket-name "$OCI_BUCKET" \
    --name "$OBJECT_NAME" \
    --file - \
    | gpg --batch --yes --quiet --decrypt --passphrase-fd 3 3<<<"$GPG_PASSPHRASE" \
    | gunzip \
    | docker exec -i "$PG_CONTAINER" \
        psql --username="$PG_USER" --dbname="$PG_DATABASE" --set ON_ERROR_STOP=1

echo "restore.ok object=${OBJECT_NAME} target=${PG_DATABASE}" >&2
