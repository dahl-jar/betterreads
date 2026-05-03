# Backend architecture

## Layout

Spring Boot, layered per feature:

- `controller`
- `service`
- `repository`
- `integration` (external API clients)
- `dto`, `entity`, `mapper`
- `exception`

Feature modules: `auth`, `catalog`, `reviews`, `collections`, `integration.openlibrary`.

## Data strategy

- **Short term:** rely on OpenLibrary for search and details, cache aggressively.
- **Medium term:** persist locally on first access (viewed books, reviewed books, books in collections, trending, popular searches).
- **Long term:** local DB is the read source for hot data; OpenLibrary handles sync and missing records.

## Stack

- Java 25
- Spring Boot 3.5
- Spring Web, Spring Data JPA, Spring Security (resource server for Cloudflare Access JWT validation)
- PostgreSQL 17 with `pg_trgm` and `unaccent`
- Caffeine cache
- WebClient
- Flyway
- Testcontainers
- JJWT for app-issued tokens, Bucket4j for rate limiting

## Implementation rules

- Controllers don't call repositories directly (ArchUnit-enforced).
- JPA entities don't cross the API boundary; DTOs do.
- Secrets come from environment, not `application.properties`.
- Spring Security owns auth.
- Global exception handling.
- DB indexes for common lookups.

## Background work

Background jobs handle work that shouldn't run inside the request path:

- Trending refresh
- Metadata enrichment
- Recommendation generation
- Search indexing updates
- Activity feed aggregation

API responses return with available data; workers handle enrichment asynchronously.

## Search

Start with PostgreSQL plus `pg_trgm` for fuzzy matching on indexed local data. Keep OpenLibrary as the source for missing records. Move to a dedicated search engine (Solr or similar) only if PostgreSQL search becomes the bottleneck.
