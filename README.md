# BetterReads

Backend service for BetterReads, a book tracking app. Spring Boot on Java 25, Postgres, deployed at [api.betterreadsapp.com](https://api.betterreadsapp.com).

Live API documentation: [api.betterreadsapp.com/swagger-ui.html](https://api.betterreadsapp.com/swagger-ui.html).

## Background

The first version of BetterReads was a school project: a Thymeleaf app that called OpenLibrary on every page load, used Spring's default session auth, and let Hibernate auto-create the schema. The original code is at [dahl-jar/legacy-betterreads](https://github.com/dahl-jar/legacy-betterreads).

This is a rebuild. The backend is a headless JSON API. Auth uses short-lived access JWTs and refresh tokens that rotate on every use, with replay detection. Schema changes go through Flyway, and the runtime database role has no DDL privileges. Production runs on a 4 GB Hetzner VM behind a Cloudflare Tunnel, with encrypted nightly backups to Cloudflare R2.

## Notable areas

Refresh-token rotation. Tokens are stored as HMAC-SHA256 hashes, rotated on every successful refresh, and travel only in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`. Replaying a previously rotated token revokes every active token for the user. Concurrent rotations are serialized via `SELECT ... FOR UPDATE` inside the rotation transaction. See `auth/refresh/RefreshTokenService.java`.

Database role separation. Flyway runs as a migration owner with DDL privileges; Spring Boot connects as a runtime role with only CRUD on data tables. Implemented in `V10__split_runtime_db_role.sql`.

Security layering. Three Spring Security filter chains: public API on `:8080` with strict CSP and JWT authentication, management endpoints on `127.0.0.1:8081` (localhost-only) gated by Cloudflare Access JWT validation, and Swagger UI on a relaxed CSP scoped to the docs paths only.

Encrypted offsite backups. `pg-backup.sh` pipes `pg_dump` through `gzip` and `gpg --symmetric` into `rclone rcat` to Cloudflare R2. Daily and weekly snapshots, with a 30-day R2 lifecycle on dailies. Restore is documented and verified in `docs/how-to/backup-postgres.md`.

Tunnel-only ingress. The VM has no public ports open. `cloudflared` opens four QUIC connections outbound to Cloudflare's edge; ingress maps `api.betterreadsapp.com → localhost:8080` and `metrics.betterreadsapp.com → localhost:8081`.

Quality gate. `./gradlew check` runs Checkstyle, PMD, SpotBugs with FindSecBugs, Error Prone, NullAway, OWASP dependency-check, JaCoCo, and JUnit 5 with Testcontainers. ArchUnit enforces the layering between controller, service, and repository. Tests use a real Postgres and the real Spring Security filter chain. Each feature carries two or three unhappy-path tests per happy-path test.

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
- [Deployment](docs/explanation/deployment.md)

How-to:
- [Deploy](docs/how-to/deploy.md)
- [Back up and restore Postgres](docs/how-to/backup-postgres.md)
- [Set up the Cloudflare Tunnel](docs/how-to/cloudflare-tunnel.md)

## License

Apache 2.0. See [LICENSE](LICENSE).
