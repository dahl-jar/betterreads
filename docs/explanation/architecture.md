# Backend architecture

## Packages

Application code is organised by feature under `com.betterreads`. The feature packages are `auth`, `catalog`, `collections`, `reviews`, `comments`, and `search`. Each feature contains the layers it uses.

External catalog, description, image, and storage clients live under `integration`. Shared web, exception, scheduling, and utility code lives under `common`. The transactional email outbox lives under `mail`.

## Dependencies

HTTP requests enter through controllers. Controllers call services, and services call repositories. API contracts use record DTOs. Repository and entity types are private to their owning feature; cross-feature calls use service interfaces and DTOs.

External response types are mapped to catalog source models inside their integration package. Catalog image code depends on the `ImageStore` port in `catalog/image/store`; the MinIO integration implements that port.

ArchUnit checks layer direction, package placement, source package contents, integration boundaries, and feature ownership in `src/test/java/com/betterreads/ArchitectureRules.java`.

## Data

Postgres stores accounts, catalog records, shelves, reviews, comments, and queued mail. Flyway applies schema migrations with the migration role. The application uses the `betterreads_app` role for data reads and writes.

Incomplete catalog records are stored in `pending_book`. Promotion writes complete records to `book`. Meilisearch indexes promoted books. Redis stores book-detail cache entries and shared rate-limit buckets. MinIO stores processed cover images.

## Authentication

The API security chain permits authentication endpoints and public catalog GETs. Other API requests require an application JWT. Refresh tokens rotate after use and detect replay. Swagger UI uses a public read-only chain. Management endpoints validate Cloudflare Access JWTs when a decoder is configured; the private health and metrics paths remain available to in-cluster scrapers.

## Background jobs

Scheduled jobs drain queued mail, delete accounts after their grace period, promote staged books, refresh catalog data, reconcile the search index, replace weak descriptions, and mirror cover images. Long-running jobs use dedicated executors with re-entry guards.

## Tests

`src/test` contains deterministic tests and static architecture checks. `src/liveTest` calls external catalog services. `src/localDbVerification` runs operator checks against a configured database. `./gradlew check` compiles and analyses all three source sets and runs the deterministic suite.
