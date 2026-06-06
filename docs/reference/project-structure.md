# Project structure

Spring Boot, organised by feature. Each feature module owns its `controller`, `service`, `repository`, `entity`, `dto`, and `mapper` subpackages; a module only carries the subpackages it needs.

```text
com.betterreads
  auth/           registration, login, JWT issuing, refresh-token rotation, account deletion, email verification, password reset
  catalog/        book entity, the source merger, staging, promotion, scheduled refresh, the book-detail read path and its SSE stream
  search/         Meilisearch client, index reconciler, the search endpoint
  integration/    one package per external source: loc, wikidata, googlebooks, openlibrary, hardcover
  mail/           transactional outbox and the worker that delivers queued mail
  operations/     Cloudflare Access audience validation for the management port
  common/         exception handling, web filters, crypto, shared DTOs and utilities
  config/         security, CORS, caching, OpenAPI, metrics, Jackson
  BetterReadsApplication
```

Rules enforced by ArchUnit (`src/test/java/com/betterreads/ArchitectureRules.java`):

- Controllers depend on services and DTOs, never on repositories.
- JPA entities do not cross the API boundary; DTO records do.
- Each external source stays isolated under `integration/<vendor>/`; source-shaped types do not reach catalog or search logic.
