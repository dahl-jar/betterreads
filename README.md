# BetterReads

A book tracking and recommendation app. Spring Boot backend on Java 25, Postgres, deployed (eventually) to OCI Ampere on the free tier. OpenLibrary supplies the catalog data.

Status: scaffolded, no features implemented yet.

## Stack

Java 25 · Spring Boot 3.5 · Postgres 17 · Flyway · Caffeine · WebClient · Gradle 9 (Kotlin DSL).

## Prerequisites

- JDK 25. Oracle GraalVM is what I use locally: `brew install --cask graalvm-jdk`. Temurin works too.
- Docker for the local Postgres.
- `JAVA_HOME` set.

## Quickstart

```bash
docker compose up -d
cp .env.example .env
./gradlew bootRun
```

Flyway applies migrations on first startup. API at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Commands

| Command | What it does |
|---|---|
| `./gradlew bootRun` | Run locally |
| `./gradlew check` | Full quality gate (Checkstyle, PMD, SpotBugs, ErrorProne, NullAway, JaCoCo, JUnit) |
| `./gradlew test` | Just tests |
| `./gradlew bootJar` | Build the JVM jar |
| `./gradlew nativeCompile` | Build a native-image binary, GraalVM only |
| `docker compose up -d` | Start Postgres |
| `docker compose down -v` | Stop and wipe Postgres |

## Layout

```text
src/main/java/com/betterreads/
  <feature>/                 controller, service, repository, entity, dto, mapper
  common/                    shared exceptions and error DTOs
  config/                    Spring config beans

src/main/resources/
  application.yml
  db/migration/              Flyway migrations

src/test/java/com/betterreads/
  Tests, plus ArchUnit boundaries

config/                      Checkstyle, PMD, OWASP rule files
docs/                        Planning notes
infra/terraform/             Always Free OCI template
```

## Architecture

Controller calls service, service calls repository. Controllers never reach into repositories directly, ArchUnit fails the build if they try. DTOs at the API are records, JPA entities never leave the service layer. OpenLibrary code is isolated under `integration/openlibrary/`.

## Deployment

Production target is OCI Always Free Ampere (aarch64). Local dev is Apple Silicon, also aarch64, so the architecture matches. The Terraform template under `infra/terraform/` provisions the VM and validates the inputs against Free Tier caps so a typo can't push the bill above zero. Native-image is there for when the 6 GB RAM ceiling gets tight, but the build runs fine in JVM mode for now.

Nothing's actually deployed yet. The infra is ready when there's something worth shipping.

## Docs

- [Overview and search](docs/01-overview-and-search.md)
- [Backend architecture](docs/02-backend-architecture-and-roadmap.md)
- [Recommendations and ML](docs/03-recommendations-and-ml.md)
- [Deployment and frontend](docs/04-deployment-and-frontend.md)
- [Project structure](docs/05-project-structure.md)
- [Database schema](docs/06-database-schema.md)
- [Current phase](docs/roadmap/current-phase.md)

## License

Apache 2.0 — see [LICENSE](LICENSE). Copyright 2026 Nanthawat Dahl.
