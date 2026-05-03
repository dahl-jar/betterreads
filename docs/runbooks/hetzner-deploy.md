# Hetzner deploy runbook

The production app runs on a Hetzner Cloud CX23 VM in Helsinki. This runbook covers updating the running app, changing config, restarting services, and rolling back if something breaks.

## Layout on the VM

```
/opt/betterreads/
  app.jar              the running Spring Boot fat jar
  app.jar.previous     the previous jar, kept for rollback
  docker-compose.yml   Postgres definition
  .env                 runtime config, owned root, mode 600
  pg-backup.sh         daily backup to R2
  pg-restore.sh        restore from R2
```

System services:

```
betterreads.service    Spring Boot via systemd, depends on docker.service
docker.service         Docker daemon (Postgres container runs here)
cloudflared.service    Cloudflare Tunnel
cron                   daily backup at 03:00 UTC
```

## Updating the jar

From your laptop:

```bash
./gradlew bootJar
ssh root@<vm-ip> 'mv /opt/betterreads/app.jar /opt/betterreads/app.jar.previous'
scp build/libs/betterreads-backend-*.jar root@<vm-ip>:/opt/betterreads/app.jar
ssh root@<vm-ip> 'systemctl restart betterreads'
curl https://api.betterreadsapp.com/healthz
```

The `mv` step before `scp` keeps a known-good copy at `app.jar.previous` for rollback.

If `/healthz` returns anything other than 200 within 30 seconds:

```bash
ssh root@<vm-ip> 'journalctl -u betterreads -n 50 --no-pager'
```

## Rolling back

```bash
ssh root@<vm-ip> 'mv /opt/betterreads/app.jar.previous /opt/betterreads/app.jar && systemctl restart betterreads'
curl https://api.betterreadsapp.com/healthz
```

## Changing environment variables

Edit `/opt/betterreads/.env` over SSH. Don't open `.env` in your local IDE — the IDE auto-attaches selections to chat (a real incident has happened in this project).

```bash
ssh root@<vm-ip> 'nvim /opt/betterreads/.env'
systemctl restart betterreads
```

Postgres also reads its credentials from this file via `docker compose`, so changes to `DB_USERNAME` / `DB_PASSWORD` need a Postgres restart too:

```bash
ssh root@<vm-ip> 'cd /opt/betterreads && docker compose restart db'
```

### Gotcha: Postgres credentials are persisted in the volume

Postgres only reads `POSTGRES_USER` / `POSTGRES_PASSWORD` from the env on **first** container start, when it initializes the data volume. After that, the credentials live in `betterreads-db-data` and the env var is ignored.

If `DB_PASSWORD` in `.env` drifts from what Postgres was initialized with, the app fails to connect with `FATAL: password authentication failed`. The only ways out are:

- Run `ALTER USER` inside Postgres to match the current `.env`. Needs a working login first, which is the chicken-and-egg if you only have the new password.
- Wipe the volume and let Flyway re-migrate from scratch. Loses data. Acceptable when there's nothing real in the DB yet.

Correct rotation flow:

```bash
# 1. Connect with the current (still-valid) credentials and ALTER USER
ssh root@<vm-ip>
docker exec -it betterreads-db psql -U betterreads -d postgres
postgres=# ALTER USER betterreads WITH PASSWORD '<new>';
postgres=# ALTER USER betterreads_app WITH PASSWORD '<new app>';
postgres=# \q

# 2. Update .env to match
nvim /opt/betterreads/.env

# 3. Restart Spring Boot. Postgres doesn't need to restart.
systemctl restart betterreads
```

If the rotation order gets out of sync (you change `.env` first, then forget the `ALTER USER` step), the container is fine but the app won't start. Reverse the order in `.env` and try again, or `ALTER USER` from another working session.

## Restarting services

```bash
ssh root@<vm-ip>

systemctl restart betterreads      # Spring Boot
docker compose -f /opt/betterreads/docker-compose.yml restart db   # Postgres
systemctl restart cloudflared      # Cloudflare Tunnel

systemctl status betterreads cloudflared docker
docker ps
```

## Logs

```bash
journalctl -u betterreads -f                 # Spring Boot, follow
journalctl -u cloudflared -f                 # tunnel
docker logs -f betterreads-db                # Postgres
journalctl -t betterreads-backup --since today    # backup cron output
```

## Disk usage

The 40 GB SSD is shared between the OS, Docker images, the Postgres data volume, and the Spring Boot jar. Check periodically:

```bash
ssh root@<vm-ip> 'df -h / && docker system df && du -sh /opt/betterreads/'
```

If Docker has accumulated unused images:

```bash
ssh root@<vm-ip> 'docker image prune -f'
```

The Postgres volume `betterreads-db-data` only grows. Don't `docker volume rm` it without confirming a recent R2 backup exists.

## Updating the OS

```bash
ssh root@<vm-ip> 'DEBIAN_FRONTEND=noninteractive apt-get update && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y'
```

If a kernel update lands, reboot the VM:

```bash
ssh root@<vm-ip> 'reboot'
```

systemd brings everything back automatically: `docker.service` → Postgres container → `betterreads.service` → cloudflared. Verify with `/healthz` once the VM is back. Expect ~30 seconds of downtime during the reboot.

## Adding the SSH key for a new operator

The VM was provisioned with one SSH key (the `Hetzner` key in 1Password). To grant a second person access:

```bash
ssh root@<vm-ip> 'echo "<new public key>" >> /root/.ssh/authorized_keys'
```

Removing keys is the same file, just edit it.

## Recreating the VM from scratch

Nothing in the codebase regenerates the current VM automatically. If the VM is destroyed, the recipe is:

1. `hcloud server create --name betterreads --type cx22 --image ubuntu-24.04 --location hel1 --ssh-key Hetzner`
2. ssh in, `apt upgrade`, install GraalVM 25, Docker, cloudflared, rclone (commands recorded in `.local/CHANGELOG.md` 2026-05-03)
3. `scp` the latest jar, `docker-compose.yml`, both backup scripts to `/opt/betterreads/`
4. Create `/opt/betterreads/.env` from values in 1Password
5. Run the systemd unit installs (`betterreads.service`, the `cloudflared service install <token>` flow)
6. `pg-restore.sh` from the most recent R2 backup, or let Flyway re-migrate on first boot if data loss is acceptable
7. Update Cloudflare Tunnel ingress rules to point at the new VM IP if the IP changed (Hetzner usually keeps it on rebuild)

The full setup is one OS plus three packages plus copying four files plus one cron line. About an hour from `hcloud server create` to working `/healthz`.
