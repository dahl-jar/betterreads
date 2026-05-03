# Deployment and frontend

## Architecture

A single Hetzner Cloud VM in Helsinki runs the backend and Postgres. Cloudflare sits in front for DNS, TLS, tunnel, Access, and R2 backups. The frontend lives in a separate repo on Cloudflare Pages.

## Backend host: Hetzner Cloud CX23

`CX23` (x86, 2 vCPU, 4 GB RAM, 40 GB SSD), Helsinki (`hel1`), Ubuntu 24.04 LTS, €4.99/mo.

The choice landed on Hetzner after OCI Always Free Ampere capacity in Amsterdam never opened. The earlier OCI plan is recorded in `.local/DECISIONS.md` along with the alternatives considered. ARM was the original architecture target via OCI Ampere; with OCI off the table the architecture lock weakened, and the x86 CX23 is €1.25/mo cheaper than Hetzner's ARM CAX11 with the same RAM.

Provisioning is one `hcloud` command rather than Terraform. Single VM, no orchestration, no Terraform state to manage.

## Database host: Postgres in Docker on the same VM

`docker-compose.yml` at `/opt/betterreads/docker-compose.yml` runs Postgres 17 Alpine bound to `127.0.0.1:5432`. Persistent named volume `betterreads-db-data` keeps data across container restarts. Bound to localhost only so the VM doesn't expose the database publicly.

V10 of the Flyway migrations creates a separate `betterreads_app` role with CRUD-only privileges. Spring Boot connects as `betterreads_app`, Flyway runs as the migration owner `betterreads`. SQL injection on the runtime can't drop tables or alter schema.

## Backups: Cloudflare R2

Daily and Sunday-weekly backups go to a Cloudflare R2 bucket via rclone. The script encrypts with GPG before upload (AES-256, passphrase from 1Password). R2 lifecycle expires anything in `betterreads/postgres/` after 30 days. The full operational details, restore drill, and disaster recovery flow live in `docs/runbooks/postgres-backup.md`.

R2 is on the same Cloudflare account as DNS, Tunnel, and Access. Free tier covers 10 GB stored, which is well above the daily-plus-weekly retention budget at portfolio scale.

## Frontend host: Cloudflare Pages, separate repo

Vite + React + TypeScript. JWT in `Authorization: Bearer` header. Separate repo (`betterreads-frontend`) because deployment paths differ from the backend.

Cloudflare Pages is free, no card on file, account already authenticated via Wrangler.

## Edge: Cloudflare

Cloudflare provides DNS, TLS, CDN, basic DDoS protection, Tunnel, Access, and R2 in one account.

`cloudflared` on the VM exposes the backend without opening firewall ports. Two ingress rules: `api.betterreadsapp.com` to `localhost:8080` (public API), `metrics.betterreadsapp.com` to `localhost:8081` (actuator behind Cloudflare Access).

Custom rate-limiting rules require a paid Cloudflare plan, so per-endpoint rate limiting lives in the backend (Bucket4j) instead.

## Deploy flow

Updating the running app:

1. Build the jar locally: `./gradlew bootJar`
2. `scp build/libs/betterreads-backend-*.jar root@<vm-ip>:/opt/betterreads/app.jar`
3. `ssh root@<vm-ip> 'systemctl restart betterreads'`
4. `curl https://api.betterreadsapp.com/healthz` to confirm 200

Spring Boot is a systemd service at `/etc/systemd/system/betterreads.service` with `Restart=on-failure`, so the restart is non-destructive: if the new jar fails to start, systemd keeps trying with the new one until it succeeds or you ssh in to fix it. There's no automatic rollback to the previous jar; that's a known gap.

For a clean rollback path, keep the previous jar at `/opt/betterreads/app.jar.previous` before scp'ing the new one. If the new jar misbehaves, `mv app.jar.previous app.jar && systemctl restart betterreads`.

The Hetzner deploy runbook at `docs/runbooks/hetzner-deploy.md` walks through this flow plus environment changes, jar rebuilds, and emergency revert.

## What this architecture deliberately doesn't do

No Terraform. One VM provisioned by one `hcloud` command isn't worth the IaC overhead. If a second VM appears, revisit.

No CI/CD pipeline yet. Deploys are manual scp + restart from the laptop. Acceptable at portfolio scale; if cadence picks up, a GitHub Action that builds and ships the jar is the next step.

No managed database. Postgres in Docker on the same VM is simple, matches local dev, and avoids paying for managed Postgres or fighting free-tier limits on Neon/Supabase.

No load balancer. Cloudflare Tunnel terminates at the edge and routes through cloudflared on the VM. Free, sufficient for portfolio traffic.
