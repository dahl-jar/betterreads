# BetterReads

A book tracking and recommendation app. Spring Boot backend on Java 25, Postgres, deployed at [api.betterreadsapp.com](https://api.betterreadsapp.com). OpenLibrary supplies the catalog data.

## Stack

Java 25 · Spring Boot 3.5 · Postgres 17 · Flyway · Caffeine · WebClient · JJWT · Bucket4j · Gradle 9 (Kotlin DSL)

## Prerequisites

- JDK 25 (Oracle GraalVM or Temurin)
- Docker
- `JAVA_HOME` set

## Quickstart

```bash
# start local Postgres
docker compose up -d

# copy the env template, then set JWT_SECRET to a 32+ byte random value
cp .env.example .env

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

# build a native binary, GraalVM only
./gradlew nativeCompile

# Postgres lifecycle
docker compose up -d        # start
docker compose down -v      # stop and wipe
```

## Architecture

Controller calls service, service calls repository. Each feature package has its own controller, service, repository, entity, dto, and mapper subpackages. Controllers never reach into repositories directly, ArchUnit fails the build if they try. DTOs at the API are records, JPA entities never leave the service layer. OpenLibrary code is isolated under `integration/openlibrary/`.

## Deployment

Production runs on a Hetzner Cloud CX23 VM in Helsinki. Spring Boot via systemd, Postgres 17 in Docker on the same host, `cloudflared` connecting to Cloudflare's edge for DNS and TLS termination. Cloudflare Access protects the management endpoints; the public API lives at `api.betterreadsapp.com`.

## Docs

Reference:
- [API](docs/reference/api.md)
- [Database schema](docs/reference/database-schema.md)
- [Project structure](docs/reference/project-structure.md)

Explanation:
- [Backend architecture](docs/explanation/architecture.md)
- [Search design](docs/explanation/search-design.md)
- [Recommendations and ML](docs/explanation/recommendations-and-ml.md)
- [Deployment and frontend](docs/explanation/deployment-and-frontend.md)

How-to:
- [Deploy](docs/how-to/deploy.md)
- [Back up and restore Postgres](docs/how-to/backup-postgres.md)
- [Set up the Cloudflare Tunnel](docs/how-to/cloudflare-tunnel.md)

## License

Apache 2.0. See [LICENSE](LICENSE).
