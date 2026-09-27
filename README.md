# BetterReads

Backend API for a book tracking app. It provides catalog search, book details, shelves, reviews, comments, and account authentication.

Catalog records combine data from Library of Congress, Wikidata, Google Books, OpenLibrary, and Hardcover. Incomplete records remain in staging until they have the fields required by the public catalog. Meilisearch serves catalog search, Redis caches book details, and MinIO stores processed cover images.

## Stack

Java 25 · Spring Boot 4.0 · Postgres 17 · Meilisearch · Redis · MinIO · Flyway · Gradle 9

## Prerequisites

- JDK 25
- Docker
- `JAVA_HOME` set

## Quickstart

```bash
# copy the environment template and set JWT_SECRET to at least 32 random bytes
cp .env.example .env

# start Postgres and Redis
docker compose -f docker/docker-compose.yml --env-file .env up -d

# run the API
./gradlew bootRun
```

The API listens on `http://localhost:8080`. Swagger UI is at `http://localhost:8080/swagger-ui.html`.

## Commands

```bash
# run the app
./gradlew bootRun

# run deterministic tests
./gradlew test

# run tests and static analysis
./gradlew check

# build the executable jar
./gradlew bootJar

# start Postgres and Redis
docker compose -f docker/docker-compose.yml --env-file .env up -d

# stop Postgres and Redis and delete the Postgres volume
docker compose -f docker/docker-compose.yml --env-file .env down -v
```

## Architecture

Each feature is one flat package under `features/`, holding its controller, service, repository, entities, and DTOs. Each external system gets its own package under `clients/`. Code that more than one slice uses lives in named shared modules such as `book`, `users`, and `web`. Slices reach each other only through interfaces in those modules, and ArchUnit checks the boundaries.

## Deployment

Production runs on a single-node k3s cluster. CI publishes the application image to GHCR, updates the Kubernetes manifests, and Argo CD applies the change. Cloudflare Tunnel routes `api.betterreadsapp.com` to the cluster.

## API

The API is documented in Swagger UI at `/swagger-ui.html`, with the committed spec in [openapi.yaml](openapi.yaml).

## License

Apache 2.0. See [LICENSE](LICENSE).
