# BetterReads

Backend service for BetterReads, a book tracking app. Spring Boot on Java 25, Postgres, deployed at [api.betterreadsapp.com](https://api.betterreadsapp.com). API documentation at [api.betterreadsapp.com/swagger-ui.html](https://api.betterreadsapp.com/swagger-ui.html).

## Background

The first version of BetterReads was a school project: a Thymeleaf app that called OpenLibrary on every page load, used Spring's default session auth, and let Hibernate auto-create the schema. The original code is at [dahl-jar/legacy-betterreads](https://github.com/dahl-jar/legacy-betterreads).

This is a rebuild. The backend is a headless JSON API; a separate web client consumes it. Auth uses short-lived access JWTs and refresh tokens that rotate on every use, with replay detection.

A catalog book is assembled from five sources: Library of Congress, Wikidata, Google Books, OpenLibrary, and Hardcover. Each field has its own source priority. Title resolves from Google Books first, authors from OpenLibrary first, rating and series from Hardcover, awards from Wikidata. Subjects are merged across all five. A book is shown only once it has a title, an author, a cover, a description, a publication year, and an ISBN; until then it stays in a staging table while the remaining sources are fetched.

Search runs on Meilisearch with typo tolerance and a relevance-score floor that drops weak fuzzy matches. A Meilisearch outage returns an empty result rather than failing the request.

Schema changes go through Flyway, and the runtime database role has no DDL privileges. Production runs on a single-node Kubernetes cluster behind a Cloudflare Tunnel, deployed by Argo CD.

## Stack

Java 25 · Spring Boot 4.0 · Postgres 17 · Meilisearch · Redis · Flyway · Caffeine · WebClient · JJWT · Bucket4j · Gradle 9 (Kotlin DSL)

## Prerequisites

- JDK 25 (Oracle GraalVM or Temurin)
- Docker
- `JAVA_HOME` set

## Quickstart

```bash
# copy the env template, then set JWT_SECRET to a 32+ byte random value
cp .env.example .env

# start local Postgres
docker compose -f docker/docker-compose.yml --env-file .env up -d

# run the app, Flyway migrates on first startup
./gradlew bootRun
```

API at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Commands

```bash
# run the app
./gradlew bootRun

# tests only
./gradlew test

# full quality gate: Checkstyle, PMD, SpotBugs, ErrorProne, NullAway, JaCoCo, JUnit
./gradlew check

# build the JVM jar
./gradlew bootJar

# build a native binary (GraalVM)
./gradlew nativeCompile

# Postgres lifecycle
docker compose -f docker/docker-compose.yml --env-file .env up -d    # start
docker compose -f docker/docker-compose.yml --env-file .env down -v   # stop and wipe
```

## Architecture

Controller calls service, service calls repository. Each feature package has its own controller, service, repository, entity, dto, and mapper subpackages. DTOs at the API are records, JPA entities never leave the service layer. Each external source has its own client, DTOs, and mapper under `integration/<vendor>/`, so source-shaped types never reach the catalog logic. ArchUnit enforces the layering. See [docs/explanation/architecture.md](docs/explanation/architecture.md).

## Deployment

Production runs on a single-node k3s cluster. The app, Postgres, and Redis run as Kubernetes workloads, synced from a Git repo by Argo CD. CI builds the container image and pushes it to GHCR after the quality gate passes.

The cluster has no public ports open. A `cloudflared` Deployment connects out to Cloudflare's edge and maps `api.betterreadsapp.com` to the in-cluster ingress, which handles DNS and TLS termination. Metrics and logs ship to Grafana Cloud via a Grafana Alloy agent in the cluster.

## Docs

Reference:
- [API](docs/reference/api.md)
- [Database schema](docs/reference/database-schema.md)
- [Project structure](docs/reference/project-structure.md)

Explanation:
- [Backend architecture](docs/explanation/architecture.md)
- [Deployment](docs/explanation/deployment.md)

How-to:
- [Deploy](docs/how-to/deploy.md)
- [Back up and restore Postgres](docs/how-to/backup-postgres.md)

## License

Apache 2.0. See [LICENSE](LICENSE).
