# Deployment

## Cluster

Production runs in the `betterreads` namespace on a single-node k3s cluster. The Spring Boot app runs as a Deployment. Postgres 17, Redis 7, Meilisearch, and MinIO run as StatefulSets with local persistent volumes.

Postgres stores application data. Redis stores cache entries and rate-limit buckets. Meilisearch serves catalog search. MinIO stores book cover images in the `betterreads-images` bucket. Cluster services are private.

The application connects to Postgres with the CRUD-only `betterreads_app` role. Flyway uses the `betterreads` migration role.

## Delivery

A push to `main` starts the quality gate and container build. CI pushes the image to GHCR with the commit SHA, updates the Kustomize image in the manifests repository, and commits that change. Argo CD applies the manifests and rolls the application Deployment.

## Traffic

A `cloudflared` Deployment opens an outbound tunnel to Cloudflare. Requests for `api.betterreadsapp.com` pass through the tunnel to the cluster ingress and application Service. Cloudflare provides DNS and TLS. Bucket4j applies endpoint rate limits in the application.

## Observability

Grafana Alloy collects application metrics, Postgres metrics, node metrics, and pod logs. It sends them to Grafana Cloud.

## Operations

See [Deploy](../how-to/deploy.md) for releases, rollback, and configuration changes.
