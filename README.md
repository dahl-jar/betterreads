# BetterReads

Book tracking and recommendation platform built with Spring Boot 3.5 on Java 25. Uses OpenLibrary as an external data source with local caching and persistence.

## Stack

Java 25 (Oracle GraalVM) · Spring Boot 3.5 · PostgreSQL 17 · Flyway · Caffeine · WebClient · Gradle 9 (Kotlin DSL)

## Prerequisites

- JDK 25 (Oracle GraalVM recommended, includes native-image): `brew install --cask graalvm-jdk`
- Docker Desktop or compatible runtime (for local Postgres)
- `JAVA_HOME` exported to the JDK install root

## Quickstart

```bash
# 1. Start local Postgres
docker compose up -d

# 2. Copy environment template (optional; defaults work for local dev)
cp .env.example .env

# 3. Run the application — Flyway applies migrations on first startup
./gradlew bootRun
```

The API will be available at `http://localhost:8080`.

## Common Commands

| Command | Purpose |
|---|---|
| `./gradlew bootRun` | Run locally with Flyway auto-migration |
| `./gradlew check` | Full quality gate: Checkstyle, PMD, SpotBugs, ErrorProne+NullAway, JaCoCo, JUnit |
| `./gradlew test` | Tests only (faster iteration) |
| `./gradlew bootJar` | Build the deployable JVM jar |
| `./gradlew nativeCompile` | Build native-image binary (slow, GraalVM only) |
| `docker compose up -d` | Start local Postgres |
| `docker compose down -v` | Stop and reset local Postgres data |

## Project Structure

```text
src/main/java/com/betterreads/
  <feature>/
    controller/   REST endpoints, thin
    service/      Business logic
    repository/   Spring Data JPA interfaces
    entity/       JPA entities (internal to persistence)
    dto/          Record-based request and response types
    mapper/       Entity <-> DTO conversion
  common/         Shared exceptions, error response DTOs
  config/         Spring configuration (Security, Cache, WebClient, Jackson)

src/main/resources/
  application.yml
  db/migration/   Flyway SQL migrations (V1__, V2__, ...)

src/test/java/com/betterreads/
  Architecture and unit tests, JUnit 5 + AssertJ

config/           Quality-tool rule sets (Checkstyle, PMD, OWASP suppressions)
docs/             Planning and architecture notes
```

## Architecture

Layered: `controller -> service -> repository`. Enforced by ArchUnit tests in `src/test/java/com/betterreads/ArchitectureRules.java`.

- Controllers are thin: validate, delegate, map.
- Services own business logic. Constructor injection only — no field injection.
- Repositories are Spring Data JPA interfaces. No business rules in queries.
- DTOs at API boundaries are records. JPA entities never leave the service layer.
- OpenLibrary integration is isolated under `integration/openlibrary/` and exposed only through service calls.

## Deployment Target

Production runs on OCI Always Free Ampere `A1.Flex` (aarch64). Local development on Apple Silicon (also aarch64), so no cross-arch surprises. When memory pressure becomes real on the constrained Ampere shape, switch from JVM-mode to native-image (`./gradlew nativeCompile`) for ~5-10x lower memory footprint.

## Documentation

- [Overview and Search](docs/01-overview-and-search.md)
- [Backend Architecture and Roadmap](docs/02-backend-architecture-and-roadmap.md)
- [Recommendations and ML](docs/03-recommendations-and-ml.md)
- [Deployment and Frontend](docs/04-deployment-and-frontend.md)
- [Project Structure](docs/05-project-structure.md)
- [Database Schema](docs/06-database-schema.md)
- [Current Phase](docs/roadmap/current-phase.md)

## License

Apache 2.0 — see [LICENSE](LICENSE). Copyright 2026 Nanthawat Dahl.
