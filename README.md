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

# start Postgres
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

# start Postgres
docker compose -f docker/docker-compose.yml --env-file .env up -d

# stop Postgres and delete its volume
docker compose -f docker/docker-compose.yml --env-file .env down -v
```

## Architecture

Code is organised by feature. Controllers expose record DTOs, services contain application logic, and repositories access Postgres. External API types remain under `integration/<vendor>/`; catalog code consumes internal source models and ports. ArchUnit checks package placement, layer dependencies, and feature ownership. See [Backend architecture](docs/explanation/architecture.md).

## Deployment

Production runs on a single-node k3s cluster. CI publishes the application image to GHCR, updates the Kubernetes manifests, and Argo CD applies the change. Cloudflare Tunnel routes `api.betterreadsapp.com` to the cluster. See [Deployment](docs/explanation/deployment.md).

## Docs

- [API reference](docs/reference/api.md)
- [Database schema](docs/reference/database-schema.md)
- [Project structure](docs/reference/project-structure.md)
- [Backend architecture](docs/explanation/architecture.md)
- [Catalog pipeline](docs/explanation/catalog-pipeline.md)
- [Deployment](docs/explanation/deployment.md)
- [Deploy the app](docs/how-to/deploy.md)
- [Back up and restore Postgres](docs/how-to/backup-postgres.md)

## License

Apache 2.0. See [LICENSE](LICENSE).
