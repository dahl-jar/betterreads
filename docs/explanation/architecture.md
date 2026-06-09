# Backend architecture

## Layout

Spring Boot, organised by feature. Each feature module owns its `controller`, `service`, `repository`, `entity`, `dto`, and `mapper` subpackages. External-API integrations live under `integration/<vendor>/`. Cross-cutting concerns (`common/exception`, `common/web`, `common/util`) sit at the top level.

The feature modules are `auth`, `catalog`, `search`, `collections` (bookshelves and reading status), `reviews` (reader reviews and the community rating), and `comments`. Under `integration/` are the five catalog sources (Library of Congress, Wikidata, Google Books, OpenLibrary, Hardcover), two description-only sources (Wikipedia, Apple Books), an image fetcher, and a MinIO client. A transactional mail outbox lives under `mail`. ArchUnit enforces the same shape across all of them.

## Stack

- Java 25
- Spring Boot 4.0
- Spring Web, Spring Data JPA, Spring Security (resource server for Cloudflare Access JWT validation)
- PostgreSQL 17
- Meilisearch for full-text catalog search
- MinIO for stored book covers
- Redis for the book-detail cache and shared rate-limit buckets
- Caffeine for in-process caches
- WebClient for the source integrations and cover downloads
- Flyway for migrations
- Testcontainers for integration tests
- JJWT for app-issued tokens, Bucket4j for rate limiting

## Implementation rules

- Controllers don't call repositories directly. ArchUnit fails the build if they try.
- The API sends and receives record DTOs. JPA entities stay in the service and repository layers.
- Secrets come from environment variables bound to `@ConfigurationProperties`, never from `application.properties`.
- Spring Security owns authentication and authorization. The public API authenticates with the app JWT; the management endpoints are bound to the internal interface and require a separate token; Swagger UI is served on its own chain.
- Global exception handling lives in `common/exception/GlobalExceptionHandler` and returns a consistent error shape.
- Database indexes for common lookups, defined in Flyway migrations.

## Data access

Spring Data JPA repositories. Read paths use derived query methods or `@Query` with named parameters. The runtime database role (`betterreads_app`) holds only `SELECT/INSERT/UPDATE/DELETE` on data tables, no DDL; Flyway migrations run as the migration owner. SQL injection on the runtime cannot drop tables or alter the schema.

## Background work

Long-running and request-path-unfriendly work runs in scheduled jobs (`@Scheduled`): a mail outbox worker drains queued mail, an hourly sweep hard-deletes accounts past their grace window, a promoter moves staging books to the catalog once they are complete, a refresh job re-pulls stale catalog data, a reconciler keeps the Meilisearch index in step with the database, and two backfills fill thin book descriptions and mirror covers into MinIO. Each backfill runs on its own single-thread executor with a re-entry guard, walks the catalog a bounded slice at a time, and is idempotent. Request handlers stay synchronous and bounded; anything slower is queued for one of these.

## Testing

Integration tests use Testcontainers to bring up a real Postgres 17 and the real Spring Security filter chain. ArchUnit contracts in `src/test/java/com/betterreads/ArchitectureRules.java` enforce the layering above. The convention on each feature is two or three unhappy-path tests for each happy-path test.
