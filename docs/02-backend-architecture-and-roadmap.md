# Backend architecture and roadmap

## Recommended backend architecture
Use Spring Boot with clear layers:
- controller
- service
- repository
- integration client
- dto
- entity
- mapper
- exception

Suggested modules:
- `auth`
- `catalog`
- `reviews`
- `collections`
- `integration.openlibrary`

## Data strategy
### Short term
Still rely on OpenLibrary for search and details, but cache aggressively.

### Medium term
Store locally:
- books that users viewed
- books that users reviewed
- books in user collections
- trending books
- popular search results

### Long term
Treat local DB as the main read source for hot data, while OpenLibrary is used for sync and missing records.

## Technology choices
Recommended stack:
- Java 25
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Spring Security with OAuth2 client and resource server
- Caffeine cache
- WebClient instead of plain RestTemplate
- Flyway for migrations
- Testcontainers for integration tests

## Important implementation rules
- Controllers should not call repositories directly.
- JPA entities should not be used as request DTOs.
- Do not store raw credentials in `application.properties`.
- Use Spring Security instead of manual session auth.
- Add global exception handling.
- Use DTOs for API responses.
- Add DB indexes for common lookups.

## Phased rebuild plan
### Phase 1 - Foundation
- Create a new Spring Boot project
- Add PostgreSQL, Flyway, Security, and WebClient
- Define package structure and DTO boundaries
- Set up environment-based config

### Phase 2 - Fast catalog search
- Build `/api/search`
- Use small OpenLibrary payloads
- Add caching for search queries
- Add pagination or result limits

### Phase 3 - Book detail flow
- Build `/api/books/{workKey}`
- Fetch rich metadata only here
- Cache work details and author details
- Add local persistence for viewed/used books
- Seed genre table from BISAC categories via Flyway
- Build subject-to-genre mapping pipeline (exact match, then keyword match)
- Add admin endpoint for reviewing unmapped subjects
- ML-assisted genre classification later if the manual queue stays large

### Phase 4 - User features and security
Authentication:
- Local registration with email and password — hash with BCrypt, require email verification before activation
- OAuth2 login with Google and GitHub using Spring Security OAuth2 client
- Link OAuth2 accounts to existing local accounts when the email matches
- Issue JWTs from the backend for both login paths
- JWT expiry and refresh token flow
- Password reset via email token
- No Auth0 or external auth service — Spring Security handles the full flow

Authorization:
- Role-based access with USER and ADMIN roles
- Data ownership checks in the service layer — users can only access their own data
- ADMIN role for moderation (reviews, duplicate books, user management)
- Public data (book metadata, public reviews) stays accessible without auth

Security headers and API protection:
- Configure CORS for the frontend origin
- Add CSP header
- HSTS, X-Content-Type-Options, X-Frame-Options come from Spring Security defaults
- Add rate limiting to prevent brute force and abuse
- Add request size limits

User features:
- Add reviews
- Add collections / reading status
- Add ownership checks and validation

### Phase 5 - Quality and performance
- Add integration tests with Testcontainers
- Add metrics and timing logs
- Add circuit breaker and fallback responses
- Tune indexes and queries

### Phase 6 - Event infrastructure with Kafka
- Add Spring Kafka producer and consumer setup
- Publish domain events: review created, book added to collection, reading status changed, book detail viewed
- Build consumers for activity feed writes and recommendation input updates
- Add dead letter topic for failed events
- Terraform the broker infrastructure on AWS (MSK or single-broker EC2)

## Good learning goals for you
This rebuild will help you practice:
- API design
- service layering
- caching
- external API integration
- database modeling
- OAuth2, JWT, and role-based access control
- API security (CORS, CSP, rate limiting)
- testing and performance thinking
- event-driven architecture with Kafka
- infrastructure as code with Terraform and AWS

## Background jobs and async work
Background jobs are allowed to be slow. User-facing API requests should not be.

Use background jobs for:
- trending refresh
- metadata enrichment
- recommendation generation
- search indexing updates
- activity feed aggregation

Do not make the API wait for background jobs to complete.

Good rule:
- API request = return fast with available data
- worker = do expensive enrichment later

## Search as a major feature
Search will likely be one of the most important features in BetterReads.

Recommended progression:
- start with PostgreSQL search and indexed local data
- keep OpenLibrary as a source for missing data
- later consider a dedicated search engine if search becomes advanced

If search becomes rich and central, Apache technologies that make more sense than Spark are:
- `Apache Solr` for search
- `Apache Lucene` as the underlying search library option
- `Apache HttpClient` for external API integration

Do not start with Spark for this project.

## Apache and event technology guidance
Possible Apache technologies for BetterReads:
- `Apache HttpClient` for robust OpenLibrary calls
- `Apache Solr` if search becomes a first-class indexed feature
- `Apache ActiveMQ` if you want a simpler queue later
- `Apache Kafka` only when event flow becomes large enough to justify it

Kafka is planned for Phase 6, after user features and quality work are in place. See the phased rebuild plan above for specifics. The use cases are activity feed fan-out, recommendation event processing, and decoupling features that currently call each other directly.
