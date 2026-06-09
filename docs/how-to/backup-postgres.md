# How to back up and restore Postgres

Postgres holds all persistent state: user accounts and the catalog. A daily CronJob dumps the database, encrypts it, and uploads to Cloudflare R2.

## What ends up in the bucket

- Daily: `betterreads/postgres/daily/pg-betterreads-YYYY-MM-DD.sql.gz.gpg`
- Weekly: `betterreads/postgres/weekly/pg-betterreads-YYYY-WNN.sql.gz.gpg` (Sundays)

Each object is `pg_dump` piped through gzip and AES-256 GPG. The job aborts if an upload lands under 1 KB, which catches empty or failed dumps.

## One-time setup

The CronJob reads its credentials from the `betterreads-backup` sealed secret. Populate it once:

- A GPG passphrase. This encrypts every backup; lose it and every backup is unrecoverable. Keep a copy somewhere durable and offline.
- An R2 token with Object Read & Write, scoped to the backups bucket. Save the Access Key ID and Secret Access Key when R2 shows them; they appear once.
- The bucket name and the R2 S3 endpoint.

Seal them with the backup sealing script, which reads the values from env vars and never writes them in plaintext, then commit the sealed output and let Argo apply it.

Add the R2 lifecycle rule so old backups expire:

```bash
wrangler r2 bucket lifecycle add betterreads-backups expire-old-backups betterreads/postgres/ --expire-days 30
```

## Verifying a backup landed

Check the last job ran and the object is in the bucket:

```bash
kubectl get cronjob postgres-backup -n betterreads
kubectl logs -n betterreads -l app.kubernetes.io/name=postgres-backup --tail=20
```

A successful run logs `backup.ok target=... size=...`. A size around a few KB means an empty schema; a size matching the live database means a real backup.

## Run a backup on demand

```bash
kubectl create job -n betterreads --from=cronjob/postgres-backup backup-manual-$(date -u +%H%M)
kubectl logs -n betterreads -l app.kubernetes.io/name=postgres-backup -f
```

## Restore drill

Run a restore at least once a quarter to confirm the backups are usable. Restore into a throwaway database, never the live one.

```bash
# pull the chosen object and decrypt on a workstation with the GPG passphrase
rclone cat "r2:betterreads-backups/betterreads/postgres/daily/pg-betterreads-<date>.sql.gz.gpg" \
  | gpg --batch --quiet --decrypt --passphrase "<passphrase>" \
  | gunzip > restore.sql

# load into a throwaway db in the cluster
kubectl exec -n betterreads postgres-0 -- createdb -U betterreads betterreads_restore
kubectl exec -i -n betterreads postgres-0 -- psql -U betterreads -d betterreads_restore < restore.sql

kubectl exec -n betterreads postgres-0 -- psql -U betterreads -d betterreads_restore \
  -c "SELECT count(*), max(created_at) FROM app_user;"

kubectl exec -n betterreads postgres-0 -- dropdb -U betterreads betterreads_restore
```

If the counts and `max(created_at)` look right, the drill passed. Note the date somewhere durable.

## Real disaster recovery

Scale the app down first so it doesn't write to a half-restored database:

```bash
kubectl scale deploy/betterreads -n betterreads --replicas=0
```

Rename the broken database out of the way rather than dropping it; forensics may be needed. Restore into a fresh `betterreads`, then scale the app back up:

```bash
kubectl exec -n betterreads postgres-0 -- psql -U betterreads -d template1 \
  -c "ALTER DATABASE betterreads RENAME TO betterreads_corrupt_$(date -u +%Y%m%d);"
kubectl exec -n betterreads postgres-0 -- createdb -U betterreads betterreads
# load the decrypted dump as in the drill, into betterreads
kubectl scale deploy/betterreads -n betterreads --replicas=1
curl https://api.betterreadsapp.com/healthz
```

Drop the renamed corrupt database once forensics is no longer expected.

## When something goes wrong

`backup.fail ... size=` under 1 KB means `pg_dump` produced almost nothing. The job logs the cause; usually an auth failure or a wrong database name.

`AccessDenied` from rclone means the R2 token can't write: token rotated and the secret has the old value, or the token is scoped to the wrong bucket.

`gpg: decryption failed` on restore is a wrong passphrase. If the passphrase was rotated since the backup was written, use the passphrase that was active then.

`pg_dump: permission denied for table X` means a recent migration created a table the backup role can't read. Grant it SELECT on the new tables rather than skipping them.

## Rotation

Rotate the GPG passphrase yearly or on suspected compromise. Reseal `betterreads-backup` with the new value; old backups stay readable with the old passphrase until they age past the 30-day retention. Don't discard the old passphrase until every object encrypted with it has expired.

R2 tokens rotate independently. The token authenticates rclone to R2; it doesn't protect backup contents. Roll it in the Cloudflare dashboard, reseal the secret, then run a backup on demand to confirm.
