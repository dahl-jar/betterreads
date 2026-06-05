# Backend architecture

## Layout

Spring Boot, organised by feature. Each feature module owns its `controller`, `service`, `repository`, `entity`, `dto`, and `mapper` subpackages. External-API integrations live under `integration/<vendor>/`. Cross-cutting concerns (`common/exception`, `common/web`, `common/util`) sit at the top level.

The currently-implemented module is `auth`. The same shape is enforced by ArchUnit for any future feature module.

## Stack

- Java 25
- Spring Boot 4.0
- Spring Web, Spring Data JPA, Spring Security (resource server for Cloudflare Access JWT validation)
- PostgreSQL 17
- Caffeine cache
- WebClient for external HTTP
- Flyway for migrations
- Testcontainers for integration tests
- JJWT for app-issued tokens, Bucket4j for rate limiting

## Implementation rules

- Controllers don't call repositories directly. ArchUnit fails the build if they try.
- JPA entities don't cross the API boundary. DTO records do.
- Secrets come from environment variables bound to `@ConfigurationProperties`, never from `application.properties`.
- Spring Security owns authentication and authorization. There are three filter chains: public API on `:8080` (JWT), management endpoints on `127.0.0.1:8081` (Cloudflare Access JWT), and Swagger UI on a relaxed CSP scoped to docs paths.
- Global exception handling lives in `common/exception/GlobalExceptionHandler` and returns a consistent error shape.
- Database indexes for common lookups, defined in Flyway migrations rather than `@Index` annotations.

## Data access

Spring Data JPA repositories. Read paths use derived query methods or `@Query` with named parameters. The runtime database role (`betterreads_app`) holds only `SELECT/INSERT/UPDATE/DELETE` on data tables, no DDL; Flyway migrations run as the migration owner. SQL injection on the runtime cannot drop tables or alter the schema.

## Background work

Long-running and request-path-unfriendly work belongs in scheduled jobs (`@Scheduled`) or a dedicated job runner. The current codebase has no scheduled jobs because the only feature module is `auth`, where every operation is synchronous and bounded.

## Testing

Integration tests use Testcontainers to bring up a real Postgres 17 and the real Spring Security filter chain. ArchUnit contracts in `src/test/java/com/betterreads/ArchitectureRules.java` enforce the layering above. The convention on each feature is two or three unhappy-path tests for each happy-path test.
