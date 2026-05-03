# How to back up and restore Postgres

Postgres on the VM is the only stateful thing in the stack. Lose it and every user account, review, and collection is gone. The backup design (cron, R2, GPG, retention) is described in [explanation/deployment-and-frontend.md](../explanation/deployment-and-frontend.md). This runbook covers operating it.

## What ends up in the bucket

- Daily: `betterreads/postgres/daily/pg-betterreads-YYYY-MM-DD.sql.gz.gpg`
- Weekly: `betterreads/postgres/weekly/pg-betterreads-YYYY-WNN.sql.gz.gpg`
- Lifecycle: anything under `betterreads/postgres/` expires after 30 days.

Each object is gzip plus AES-256 GPG. The smallest healthy dump (empty schema with Flyway migrations applied) is around 4 KB encrypted; the script aborts if an upload lands under 1 KB.

## Required setup, one time

Generate a long random passphrase with `openssl rand -base64 32`. This passphrase encrypts every backup; lose it and every backup is unrecoverable. Mirror it offline.

For R2 auth, create an Account API token at Cloudflare dashboard, R2, Manage R2 API Tokens. Use `Object Read & Write`, scoped to the `betterreads-backups` bucket. In Client IP Address Filtering add both the VM's IPv4 and IPv6 to Include; an IPv4-only filter blocks IPv6 connections the OS may prefer. Save the Access Key ID and Secret Access Key before clicking past the "shown only once" screen.

Add the lifecycle policy:

```bash
wrangler r2 bucket lifecycle add betterreads-backups expire-old-backups betterreads/postgres/ --expire-days 30
```

The scripts live at `/opt/betterreads/pg-backup.sh` and `/opt/betterreads/pg-restore.sh`. The env file at `/opt/betterreads/.env` (the same file Spring Boot reads) carries the runtime config. Backup-related entries:

```
PG_CONTAINER=betterreads-db
DB_NAME=betterreads
DB_USERNAME=betterreads
BACKUP_GPG_PASSPHRASE=<secret value>
R2_ACCESS_KEY_ID=<secret value>
R2_SECRET_ACCESS_KEY=<secret value>
R2_BUCKET=betterreads-backups
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
```

`DB_NAME` and `DB_USERNAME` are reused from the Spring Boot config. The R2 vars and the GPG passphrase are backup-specific.

Install the cron entry from `infra/backup/crontab.example` for root (`sudo crontab -e`).

## Verifying a backup landed

```bash
ssh root@<vm-ip>
set -a && source /opt/betterreads/.env && set +a
export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT"
rclone ls "r2:${R2_BUCKET}/betterreads/postgres/daily/"
```

The most recent entry should be from the last 03:00 UTC. Sizes around 4 KB mean an empty schema; sizes that match the live database mean a real backup landed.

## Restore drill

Run a restore at least once a quarter. A backup that hasn't been restored is theatre.

The drill restores into a throwaway database called `betterreads_restore`, never the live `betterreads`. The script refuses to write into the live name.

```bash
ssh root@<vm-ip>

set -a && source /opt/betterreads/.env && set +a
export DB_NAME=betterreads_restore
export OBJECT_NAME=betterreads/postgres/daily/pg-betterreads-$(date -u +%Y-%m-%d).sql.gz.gpg

docker exec betterreads-db createdb -U betterreads betterreads_restore

/opt/betterreads/pg-restore.sh

docker exec betterreads-db psql -U betterreads -d betterreads_restore \
    -c "SELECT count(*) FROM app_user; SELECT max(created_at) FROM app_user;"

docker exec betterreads-db dropdb -U betterreads betterreads_restore
```

If the row counts and `max(created_at)` look right, the drill passed. Note the date in `.local/CHANGELOG.md` so the next operator knows when the last verified restore happened.

## Real disaster recovery

Stop the app first so it doesn't write to a half-restored database:

```bash
systemctl stop betterreads
```

Decide which backup to restore from. Usually the most recent daily; fall back to a weekly if the daily was taken after the corruption.

Don't drop the broken database; forensics may be needed. Rename it out of the way and create an empty database. Connect via `template1` since `ALTER DATABASE` doesn't work on a database with active connections to it:

```bash
docker exec betterreads-db psql -U betterreads -d template1 \
    -c "ALTER DATABASE betterreads RENAME TO betterreads_corrupt_$(date -u +%Y%m%d);"
docker exec betterreads-db createdb -U betterreads betterreads
```

Restore into a `betterreads_restore` throwaway and rename. The script refuses to write directly into `betterreads`, which forces this two-step.

```bash
set -a && source /opt/betterreads/.env && set +a
export DB_NAME=betterreads_restore
export OBJECT_NAME=<chosen object path>

docker exec betterreads-db createdb -U betterreads betterreads_restore
/opt/betterreads/pg-restore.sh
docker exec betterreads-db psql -U betterreads -d template1 -c "DROP DATABASE betterreads;"
docker exec betterreads-db psql -U betterreads -d template1 \
    -c "ALTER DATABASE betterreads_restore RENAME TO betterreads;"
```

Bring the app back up and verify:

```bash
systemctl start betterreads
curl https://api.betterreadsapp.com/healthz
```

Drop the renamed corrupt database once forensics is no longer expected.

## When something has gone wrong

`missing required env: VAR` in cron mail means `/opt/betterreads/.env` isn't being sourced or is malformed. The script names the variable.

`AccessDenied` from rclone means the R2 token can't write. Common causes in order: token rotated and `.env` has the old value; IP filter doesn't include the VM's IPv6 address; token scoped to the wrong bucket. Verify the Access Key ID in `.env` matches an active token in the dashboard.

`gpg: decryption failed: Bad session key` during a restore is a wrong passphrase. If the passphrase was rotated since the backup was written, the rotation didn't propagate cleanly.

A backup that uploads under 1 KB means `pg_dump` produced almost nothing. The cron mail body has the captured stderr. Usually an auth failure inside the postgres container or a wrong database name in env.

`pg_dump` failing with "permission denied for table X" means a recent Flyway migration created a table the backup user can't see. Grant SELECT to the backup user on the new tables. Don't patch the script to skip them.

## Passphrase rotation

Rotate yearly, or sooner if compromise is suspected. Shape: introduce a new passphrase, re-encrypt recent backups, then cut over.

Generate the new passphrase with `openssl rand -base64 32` and label it `betterreads-backup-gpg-rotation-YYYY`. Pull the most recent daily and weekly to a workstation, decrypt with the old passphrase, re-encrypt with the new, upload back with a `-rerolled` suffix.

Update `/opt/betterreads/.env` with the new passphrase. Wait a week. New backups during the week use the new passphrase.

Once the week passes and daily backups look healthy, promote the rotation entry to primary. Keep the old one labelled as a breadcrumb.

Don't delete the old passphrase until every object encrypted with it has aged past the 30-day retention window. Set a 35-day reminder.

## R2 token rotation

R2 tokens rotate independently of the gpg passphrase. The token authenticates rclone to R2; it does not protect backup contents. A leaked token lets someone read or overwrite the bucket but not decrypt anything inside.

In the Cloudflare dashboard, edit the active token and click Roll. Save the new credentials, update `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` in `/opt/betterreads/.env` over SSH, then run `pg-backup.sh` manually to confirm.
