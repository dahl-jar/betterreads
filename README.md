# BetterReads

A book tracking and recommendation app. Spring Boot backend on Java 25, Postgres, deployed (eventually) to OCI Ampere on the free tier. OpenLibrary supplies the catalog data.

## Stack

Java 25 · Spring Boot 3.5 · Postgres 17 · Flyway · Caffeine · WebClient · JJWT · Bucket4j · Gradle 9 (Kotlin DSL).

## Prerequisites

- JDK 25. Oracle GraalVM is what I use locally: `brew install --cask graalvm-jdk`. Temurin works too.
- Docker for the local Postgres.
- `JAVA_HOME` set.

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

Production target is OCI Always Free Ampere (aarch64). Local dev is Apple Silicon, also aarch64, so the architecture matches. The Terraform template under `infra/terraform/` provisions the VM and validates the inputs against Free Tier caps so a typo can't push the bill above zero. Native-image is there for when the 6 GB RAM ceiling gets tight, but the build runs fine in JVM mode for now.

## Docs

- [Overview and search](docs/01-overview-and-search.md)
- [Backend architecture](docs/02-backend-architecture-and-roadmap.md)
- [Recommendations and ML](docs/03-recommendations-and-ml.md)
- [Deployment and frontend](docs/04-deployment-and-frontend.md)
- [Project structure](docs/05-project-structure.md)
- [Database schema](docs/06-database-schema.md)

## License

Apache 2.0. See [LICENSE](LICENSE). Copyright 2026 Nanthawat Dahl.
