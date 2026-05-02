# Deployment and frontend

## Architecture

Single Ampere VM on OCI runs the backend and Postgres. Cloudflare sits in front for DNS, TLS, and CDN. The frontend lives in a separate repo on Cloudflare Pages.

## Backend host: OCI Always Free Ampere

`VM.Standard.A1.Flex` (aarch64). Local dev is Apple Silicon, also aarch64, so the architecture matches.

The Terraform template under `infra/terraform/` provisions one VM, one VCN with a public subnet, and one Object Storage bucket. Variable validation rejects values that fall outside Free Tier caps — a typo can't push the bill above zero. The budget alert in the OCI console catches anything that slips through.

Native-image is the upgrade path when JVM-mode RAM gets tight on the 6 GB Ampere shape. The build runs fine in JVM mode for now.

## Database host: Postgres in Docker on the same VM

Same `docker-compose.yml` runs locally and on the VM. The Compose file binds Postgres to `127.0.0.1:5433` so the VM doesn't accidentally expose the database publicly.

No managed Postgres provider. Free, simple, matches local dev exactly. Vendor lock-in is zero.

## Frontend host: Cloudflare Pages, separate repo

Vite + React + TypeScript. JWT in `Authorization: Bearer` header. Separate repo (`betterreads-frontend`) because deployment paths differ from the backend.

Cloudflare Pages is free, no card on file, account already authenticated via Wrangler.

## Edge: Cloudflare Free

Cloudflare Free Tier provides DNS, TLS, CDN, and basic DDoS protection. Custom rate-limiting rules require a paid Cloudflare plan, so per-endpoint rate limiting lives in the backend (Bucket4j) instead.

Cloudflare Tunnel (`cloudflared`) on the VM exposes the backend without opening firewall ports. Avoids the OCI Flexible Load Balancer (which would consume the single Always Free LB allocation).

## Deploy flow

When the VM exists:

1. Generate `DB_PASSWORD` and `JWT_SECRET` locally with `openssl rand -base64 32` and `openssl rand -base64 64`.
2. Build a populated `.env.vm` (gitignored, never committed).
3. `scp docker-compose.yml .env.vm <user>@<vm-ip>:~/.env`.
4. SSH in, `docker compose up -d`. Flyway applies migrations on first Spring Boot startup.
5. Point Cloudflare DNS at the VM IP (or run `cloudflared tunnel` for a tunnel-only setup).

Subsequent deploys ship a new jar or a new image and restart the container. No re-provisioning.

## Terraform scope

Terraform owns infrastructure: VCN, subnet, internet gateway, A1.Flex instance, Object Storage bucket. It does not own runtime config or secrets. Container orchestration is `docker-compose.yml` on the VM, not Terraform.
