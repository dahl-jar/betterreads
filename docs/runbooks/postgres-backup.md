# Postgres backup runbook

Postgres on the OCI VM is the only stateful thing in the stack. Lose it and every user account, review, and collection is gone. This runbook is the thing you read when that's about to happen.

## Overview

A host cron on the VM runs `infra/backup/pg-backup.sh` at 03:00 UTC every night. The script streams a `pg_dump` through gzip and gpg and uploads the result to the project's OCI Object Storage bucket using instance principal authentication. On Sundays it also writes a weekly snapshot at a separate path. Retention is enforced server-side by an OCI lifecycle policy; the script never deletes anything itself, so a buggy script can't wipe history.

The encrypted object wraps a plain SQL dump, so anyone with the gpg passphrase and read access to the bucket can restore it from a laptop. Nothing in here is proprietary.

## What ends up in the bucket

Daily snapshots live under `betterreads/postgres/daily/pg-betterreads-YYYY-MM-DD.sql.gz.gpg` and the lifecycle policy expires them after eight days. Weekly snapshots live under `betterreads/postgres/weekly/pg-betterreads-YYYY-WNN.sql.gz.gpg` and expire after thirty-five days. Each object is gzip plus AES-256 GPG, so size is roughly a fifth to a tenth of a raw dump. The smallest healthy dump (empty schema with Flyway migrations applied) lands around four kilobytes encrypted; anything below one kilobyte is a bug, and the script aborts at that floor.

## Required setup, one time

The scripts assume two things on the VM and one thing in OCI: a working passphrase, an instance principal that can write to the bucket, and a lifecycle policy that handles retention. None of this is daily-touch territory, but the day a backup needs to land is a bad day to discover one of them isn't set up.

The passphrase lives in a 1Password item called `betterreads-backup-gpg`. Use a long random value. This passphrase encrypts every backup; lose it and every backup is unrecoverable. Mirror it offline (paper in an envelope, a second password store, whatever matches how you actually handle disaster planning) before depending on it.

For OCI auth, create a dynamic group whose rule is `instance.compartment.id = '<compartment-ocid>'` and attach a policy on the same compartment:

```
Allow dynamic-group betterreads-backup-writers to manage objects in compartment id <compartment-ocid> where target.bucket.name='app-artifacts'
```

This is what lets the VM write to the bucket without an API key sitting on disk.

The bucket needs a lifecycle policy too. Two rules: delete objects under `betterreads/postgres/daily/` after eight days, and delete objects under `betterreads/postgres/weekly/` after thirty-five days. The script never deletes anything itself, so this is the only retention mechanism.

On the VM, the contents of `infra/backup/` belong at `/opt/betterreads/infra/backup/`. The standard `scp` step of the deploy flow puts them there. The env file at `/etc/default/betterreads-backup` carries the runtime config:

```
PG_CONTAINER=betterreads-db
PG_DATABASE=betterreads
PG_USER=betterreads
GPG_PASSPHRASE=<from 1Password>
OCI_BUCKET=app-artifacts
OCI_NAMESPACE=axjajfyvfvy2
```

Set it `chmod 640`, owned `root:ubuntu`, so the cron-as-ubuntu job can read it but unrelated processes can't. Then install the cron entry from `infra/backup/crontab.example` for the `ubuntu` user. The next 03:00 UTC produces the first backup.

## Verifying a backup landed

```bash
oci os object list \
    --namespace axjajfyvfvy2 \
    --bucket-name app-artifacts \
    --prefix betterreads/postgres/daily/ \
    --query 'data[*].{name:name,size:size,modified:"time-modified"}' \
    --output table
```

The most recent entry should be from the last completed 03:00 UTC.

## Restore drill

Run a restore at least once a quarter, even when nothing is broken. A backup that hasn't been restored is theatre. Picking a random Tuesday morning to do this catches three real failure modes: a lifecycle policy that deletes more aggressively than expected, a gpg passphrase rotation that never propagated to the VM, and `pg_dump` schema drift after a Flyway migration touched something the backup user can't see.

The drill restores into a throwaway database called `betterreads_restore`, never the live `betterreads`. The script refuses to write into the live name anyway.

```bash
ssh <vm-user>@<vm-ip>

source /etc/default/betterreads-backup
export PG_DATABASE=betterreads_restore
export OBJECT_NAME=betterreads/postgres/daily/pg-betterreads-$(date -u +%Y-%m-%d).sql.gz.gpg

docker exec -u postgres betterreads-db createdb betterreads_restore

/opt/betterreads/infra/backup/pg-restore.sh

docker exec betterreads-db psql -U betterreads -d betterreads_restore \
    -c "SELECT count(*) FROM app_user; SELECT max(created_at) FROM app_user;"

docker exec -u postgres betterreads-db dropdb betterreads_restore
```

If the row counts and `max(created_at)` look right, the drill passed. Note the date in `.local/CHANGELOG.md` so future-you knows when the last verified restore happened.

## Real disaster recovery

This is the real one: the live database is gone or corrupt and the path back to a working service runs through this section.

Stop the app first so it doesn't start writing to a half-restored database:

```bash
docker compose stop app
```

Decide which backup to restore from. Usually the most recent daily; fall back to a weekly if the daily was taken after the corruption was introduced.

Don't drop the broken database, you may want forensics. Rename it out of the way and create a fresh empty database in its place:

```bash
docker exec -u postgres betterreads-db psql \
    -c "ALTER DATABASE betterreads RENAME TO betterreads_corrupt_$(date -u +%Y%m%d);"
docker exec -u postgres betterreads-db createdb betterreads
```

Restore into a `betterreads_restore` throwaway and rename. The script refuses to write directly into `betterreads`, which forces this two-step on purpose.

```bash
source /etc/default/betterreads-backup
export PG_DATABASE=betterreads_restore
export OBJECT_NAME=<chosen object path>

docker exec -u postgres betterreads-db createdb betterreads_restore
/opt/betterreads/infra/backup/pg-restore.sh
docker exec -u postgres betterreads-db psql -c "DROP DATABASE betterreads;"
docker exec -u postgres betterreads-db psql \
    -c "ALTER DATABASE betterreads_restore RENAME TO betterreads;"
```

Bring the app back up, hit `/healthz` to confirm 200, and log in with a known account to confirm the restore landed:

```bash
docker compose start app
```

Drop the renamed corrupt database once you're confident no forensics is coming.

## When something has gone wrong

A cron mail saying "missing required env: VAR" means the env file at `/etc/default/betterreads-backup` either isn't being sourced or is malformed. The script names the variable it couldn't read, which is almost always a stray comment or a missing newline.

`NotAuthenticated` from `oci os object put` is the instance principal failing to reach the bucket. The dynamic group's matching rule has to resolve to this specific VM (check with `oci iam dynamic-group list`), and the policy text has to grant `manage objects` on the bucket name in the env file. Either one being wrong produces the same error message, which is annoying. Check both before assuming one is the cause.

`gpg: decryption failed: Bad session key` during a restore is the passphrase being wrong. Pull it from 1Password and try again. If it's been rotated since this particular backup was written, the rotation didn't propagate cleanly; the rotation section below covers what should have happened.

A backup that uploads but lands under one kilobyte means `pg_dump` produced almost nothing. The cron mail body has the captured stderr. Usually this is an authentication failure inside the postgres container, or the env file is pointing at the wrong database name. Read the stderr before assuming the script is broken.

`pg_dump` failing with "permission denied for table X" means a recent Flyway migration created a table that the backup user can't see. Grant SELECT to the backup user on the new tables. Don't patch the script to skip them; the backup is supposed to be complete.

## Passphrase rotation

Rotate the gpg passphrase yearly, or sooner if you suspect compromise. The shape of the rotation is: introduce a new passphrase, re-encrypt the recent backups so they survive the old passphrase being deleted, then cut over.

Generate a new passphrase and store it in 1Password as `betterreads-backup-gpg-rotation-YYYY`. Pull the most recent daily and the most recent weekly down to a workstation, decrypt with the old passphrase, re-encrypt with the new one, upload them back with a `-rerolled` suffix so the originals stay intact for now.

Update `/etc/default/betterreads-backup` on the VM with the new passphrase. Wait one full week. New backups produced during this week are encrypted with the new passphrase by design.

Once the week has passed and the new daily backups look healthy, replace the primary 1Password item's value with the new passphrase. Keep the rotation entry as a history breadcrumb.

Don't delete the old passphrase from 1Password until every object encrypted with it has aged past the lifecycle policy's retention window. Thirty-five days is the longest. Set a calendar reminder for forty.

## Out of scope

Point-in-time recovery via WAL streaming is not part of this setup. A daily snapshot plus a weekly is enough loss tolerance for personal scale; the engineering cost of running a WAL archive sidecar isn't justified yet.

Cross-region replication isn't either. The Free Tier is one region. Object Storage in `eu-amsterdam-1` is replicated within the region by Oracle, which covers single-AD failure but not a region-wide outage. A region-wide outage would lose the most recent daily backup, which is the cost of running on Free Tier.

The restore drill is currently manual on a quarterly cadence. If quarterly drills start slipping (you'll know because there's no recent CHANGELOG entry confirming one), automate it as a separate cron that restores into a throwaway and runs a row-count check.
