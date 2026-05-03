# Deployment and frontend

## Architecture

A single Hetzner Cloud VM in Helsinki runs the backend and Postgres. Cloudflare sits in front for DNS, TLS, tunnel, Access, and R2 backups. The frontend lives in a separate repo on Cloudflare Pages.

## Backend host

Hetzner Cloud `CX23` (x86, 2 vCPU, 4 GB RAM, 40 GB SSD), Helsinki (`hel1`), Ubuntu 24.04 LTS. Provisioned via one `hcloud` command.

## Database host

`docker-compose.yml` at `/opt/betterreads/docker-compose.yml` runs Postgres 17 Alpine bound to `127.0.0.1:5432`. Persistent named volume `betterreads-db-data` keeps data across container restarts. Localhost-only binding keeps the database off the public network.

Flyway V10 creates a separate `betterreads_app` role with CRUD-only privileges. Spring Boot connects as `betterreads_app`; Flyway runs as the migration owner `betterreads`. SQL injection on the runtime can't drop tables or alter schema.

## Backups

Daily and Sunday-weekly backups go to a Cloudflare R2 bucket via rclone. The script encrypts with GPG before upload (AES-256, env-supplied passphrase). R2 lifecycle expires anything in `betterreads/postgres/` after 30 days. Operational details, restore drill, and disaster recovery flow live in [how-to/backup-postgres.md](../how-to/backup-postgres.md).

## Frontend host

Vite + React + TypeScript on Cloudflare Pages. JWT in `Authorization: Bearer` header, refresh tokens via `POST /api/v1/auth/refresh`. Separate repo (`betterreads-frontend`) because deployment paths differ from the backend.

## Edge

Cloudflare provides DNS, TLS, CDN, DDoS protection, Tunnel, Access, and R2 in one account.

`cloudflared` on the VM exposes the backend without opening firewall ports. Two ingress rules: `api.betterreadsapp.com` to `localhost:8080` (public API), `metrics.betterreadsapp.com` to `localhost:8081` (actuator behind Cloudflare Access).

Per-endpoint rate limiting lives in the backend via Bucket4j with Cloudflare CIDRs as trusted proxies for `X-Forwarded-For` resolution.

## Deploy flow

Operational details are in [how-to/deploy.md](../how-to/deploy.md). Spring Boot runs as a systemd service with `Restart=on-failure`. Deploys are `scp` of a new jar plus `systemctl restart betterreads`. The previous jar is kept at `app.jar.previous` for rollback.
