# Project structure

Spring Boot, organised by feature. Each feature module owns its `controller`, `service`, `repository`, `entity`, `dto`, and `mapper` subpackages; a module only carries the subpackages it needs.

```text
com.betterreads
  auth/           registration, login, JWT issuing, refresh-token rotation, account deletion, email verification, password reset
  catalog/        book entity, the source merger, staging, promotion, scheduled refresh, description and cover backfills, the book-detail read path, the cover image endpoint, and the SSE stream
  collections/    bookshelves and per-book reading status
  reviews/        reader reviews and the BetterReads community rating
  comments/       comments on books and reviews
  search/         Meilisearch client, index reconciler, the search endpoint
  integration/    catalog sources (loc, wikidata, googlebooks, openlibrary, hardcover), description-only sources (wikipedia, itunes), the cover image fetcher, and the MinIO client
  mail/           transactional outbox and the worker that delivers queued mail
  operations/     Cloudflare Access audience validation for the management port
  common/         exception handling, web filters, crypto, shared DTOs and utilities
  config/         security, CORS, caching, OpenAPI, metrics, Jackson
  BetterReadsApplication
```

Rules enforced by ArchUnit (`src/test/java/com/betterreads/ArchitectureRules.java`):

- Controllers depend on services and DTOs, never on repositories.
- The API uses record DTOs; JPA entities stay in the service and repository layers.
- Each external source stays isolated under `integration/<vendor>/`; source-shaped types do not reach catalog or search logic.
