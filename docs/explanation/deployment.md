# Deployment

## Architecture

Production runs on a single-node k3s cluster. The Spring Boot app, Postgres, and Redis run as Kubernetes workloads. Argo CD syncs them from a Git repo (GitOps), and Cloudflare sits in front for DNS, TLS, and the tunnel.

## Workloads

The app runs as a Deployment pulling its image from GHCR. Postgres 17 and Redis 7 run as StatefulSets, each with its own PersistentVolumeClaim on the cluster's local-path storage. All three live in the `betterreads` namespace. Services are cluster-internal; nothing is published to the host network.

Flyway creates a separate `betterreads_app` role with CRUD-only privileges. Spring Boot connects as `betterreads_app`; Flyway runs as the migration owner `betterreads`. SQL injection on the runtime path can't drop tables or alter schema.

## Delivery

CI builds the container image on push to `main`, gated behind the quality check (`./gradlew check`), and pushes it to GHCR. Argo CD watches the manifests repo and reconciles the cluster to match. A deploy is a Git commit; Argo applies it and rolls the Deployment.

## Edge

Cloudflare provides DNS, TLS, CDN, and DDoS protection. A `cloudflared` Deployment in the cluster connects out to Cloudflare's edge, so no inbound ports are open on the host. The tunnel routes `api.betterreadsapp.com` to the in-cluster Traefik ingress, which host-routes to the app Service.

Per-endpoint rate limiting lives in the backend via Bucket4j, with Cloudflare CIDRs as trusted proxies for `X-Forwarded-For` resolution.

## Observability

A Grafana Alloy agent in the cluster scrapes the app's actuator metrics, Postgres metrics, and node metrics, and ships them with pod logs to Grafana Cloud. Alert rules and dashboards live in the Grafana Cloud stack.

## Operating it

See [how-to/deploy.md](../how-to/deploy.md) for updating the app, rolling back, and changing config.
