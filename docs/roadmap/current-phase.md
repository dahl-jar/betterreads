# Current phase: Phase 1 - persistence and platform baseline

## What exists

- Spring Boot project with quality gate tooling already configured
- Flyway migrations V1 through V6 covering users, books, authors, reviews, collections, interactions, recommendations, and activity events
- Feature package folders already created under `src/main/java/com/betterreads/`
- ArchUnit rules for layered architecture
- No Java classes beyond `BetterReadsApplication.java`

## What this phase delivers

Phase 1 is the setup phase that makes the backend real. By the end of it, the application should boot against PostgreSQL, Flyway should own the schema, shared configuration should be in place, shared API error handling should exist, and the first catalog and auth persistence classes should be ready for Phase 2.

This is a walkthrough guide. Each section below tells you which file to create or update, what type it should be, what must go inside it, and what to leave out.

## Scope of this phase

Build these pieces now:

- shared Spring configuration
- shared API error handling
- catalog entities and repositories
- auth user entity and repository
- application YAML configuration

Do not build these pieces yet:

- search controllers or services
- OpenLibrary client implementation
- book detail enrichment
- review Java code
- collection Java code
- authentication flows, JWT handling, or endpoint authorization rules
- recommendation or activity-feed Java code

The database already has tables for later phases. That does not mean the Java code for those phases belongs here.

## Design rules for every file in this phase

- Match the Flyway schema exactly. Hibernate validates in this phase; it does not design the schema.
- Keep layering clean: `controller -> service -> repository`. This phase stops before controllers and services, so do not invent shortcuts around that later structure.
- Keep each class small and single-purpose. If a file starts mixing unrelated responsibilities, split it.
- Use constructor injection only where dependencies exist.
- Do not expose entities directly to future API responses.
- Do not add speculative methods just because they might be useful later.

### SOLID focus

- SRP: each config class should own one area of framework setup, and each entity should map one table cleanly.
- OCP: configure shared infrastructure so later phases can extend it without replacing the baseline.
- ISP: repository interfaces should expose only the queries needed now and in the immediate next phase.
- DIP: later features should consume Spring-managed beans and repository interfaces, not concrete hacks.

---

## Step 1: shared Spring configuration

Create four files in `src/main/java/com/betterreads/config/`.

### 1. `src/main/java/com/betterreads/config/SecurityConfig.java`

Type:

- class

Annotations:

- `@Configuration`
- `@EnableWebSecurity`

This file should contain exactly two bean methods:

1. `securityFilterChain(...)`
2. `passwordEncoder()`

What `securityFilterChain(...)` must do:

- build the application security filter chain
- allow every request for now
- disable CSRF
- set session creation policy to stateless
- include a short TODO noting that Phase 4 replaces permit-all with real endpoint rules

What `passwordEncoder()` must do:

- return a BCrypt password encoder
- use the default strength unless there is a project-wide reason to change it later

What this file must not do:

- no JWT parsing
- no custom auth provider
- no user lookup logic
- no controller or repository access

Why it exists:

- the project needs an explicit security baseline now, even though real auth is later

### 2. `src/main/java/com/betterreads/config/WebClientConfig.java`

Type:

- class

Annotations:

- `@Configuration`

This file should contain one bean method:

1. `openLibraryWebClient(...)`

What `openLibraryWebClient(...)` must do:

- create the WebClient used for OpenLibrary traffic
- expose it with a bean name or qualifier that makes it clearly OpenLibrary-specific
- read the base URL from `openlibrary.base-url`
- configure a connect timeout of 5000 ms
- configure a read timeout of 10000 ms
- send a real `User-Agent` header on requests

What should be inside this file besides the bean method:

- any private constants needed for timeout values or header names
- any private helper code needed to keep the bean method readable

What this file must not do:

- no HTTP request methods for specific endpoints
- no DTO mapping
- no retry logic
- no caching logic

Important note:

- do not leave a fake contact placeholder in the `User-Agent`. If contact information is needed, make it configuration-driven.

### 3. `src/main/java/com/betterreads/config/CacheConfig.java`

Type:

- class

Annotations:

- `@Configuration`
- `@EnableCaching`

This file should contain one bean method:

1. `cacheManager()`

It may also contain private helper methods if that keeps the class small.

What `cacheManager()` must do:

- create the cache manager used by Spring caching
- register three named caches
- give each cache its own TTL and max size

The caches must be:

- `searchResults`: 15 minutes, max 500 entries
- `bookDetails`: 60 minutes, max 1000 entries
- `authorDetails`: 60 minutes, max 500 entries

What this file must not do:

- no YAML-driven cache specs for this phase
- no manual eviction logic
- no business-specific cache invalidation rules

Why it exists:

- Phase 2 search and book detail work needs caching ready before the service layer is built

### 4. `src/main/java/com/betterreads/config/JacksonConfig.java`

Type:

- class

Annotations:

- `@Configuration`

This file should contain one bean method. Use one of these approaches, not both:

- `objectMapper()`
- or a Jackson builder/customizer bean that applies the same settings

What the bean must configure:

- unknown JSON properties do not fail deserialization
- Java time types are supported
- dates serialize as ISO-8601 strings, not timestamps
- null fields are omitted from serialized JSON

What this file must not do:

- no feature-specific serializers
- no OpenLibrary DTO logic
- no controller response shaping

Why it exists:

- OpenLibrary payloads change over time, and the project also needs predictable JSON defaults for internal APIs later

---

## Step 2: shared error handling

Create four files under `src/main/java/com/betterreads/common/`.

### 5. `src/main/java/com/betterreads/common/dto/ApiErrorResponse.java`

Type:

- record

This file should contain:

- the top-level `ApiErrorResponse` record
- one static inner record for field-level validation errors

Fields for `ApiErrorResponse`:

- `status`
- `message`
- `timestamp`
- `fieldErrors`

Fields for the inner field-error record:

- `field`
- `message`

What this file must do:

- define the single error response shape used across the application
- allow `fieldErrors` to be absent for non-validation failures

What this file must not do:

- no business logic
- no mapping logic beyond what the record shape naturally provides

### 6. `src/main/java/com/betterreads/common/exception/ResourceNotFoundException.java`

Type:

- class extending `RuntimeException`

This file should contain one constructor.

What the constructor must accept:

- a resource name
- an identifier label or identifier value needed to build a useful message

What the constructor must do:

- build a message like `Book not found with workKey: /works/OL123W`

What this file must not do:

- no extra fields unless they are genuinely needed for later handling
- no HTTP concerns inside the exception itself

### 7. `src/main/java/com/betterreads/common/exception/BusinessRuleException.java`

Type:

- class extending `RuntimeException`

This file should contain one constructor.

What the constructor must accept:

- a message string

What this file is for:

- business-rule failures such as duplicate review attempts or duplicate collection entries in later phases

What this file must not do:

- no status code logic inside the exception
- no feature-specific subclasses in this phase

### 8. `src/main/java/com/betterreads/common/exception/GlobalExceptionHandler.java`

Type:

- class

Annotations:

- `@RestControllerAdvice`

This file should contain four handler methods:

1. one for `MethodArgumentNotValidException`
2. one for `ResourceNotFoundException`
3. one for `BusinessRuleException`
4. one for generic `Exception`

It may also contain private helper methods if needed to keep the handlers readable.

What the validation handler must do:

- return HTTP 400
- collect every field validation error, not just the first one
- build an `ApiErrorResponse` with populated `fieldErrors`

What the not-found handler must do:

- return HTTP 404
- build an `ApiErrorResponse` without field errors

What the business-rule handler must do:

- return HTTP 409 in this phase
- build an `ApiErrorResponse` without field errors

What the generic handler must do:

- return HTTP 500
- return the message `An unexpected error occurred`
- never expose the original exception message in the response body

Logging rules inside this file:

- log 4xx problems at WARN with concise messages
- log 500 failures at ERROR with stack traces
- never log passwords, tokens, API keys, or other secrets

What this file must not do:

- no controller-specific branching
- no custom response format per feature

---

## Step 3: catalog persistence baseline

Create six files under `src/main/java/com/betterreads/catalog/`.

### 9. `src/main/java/com/betterreads/catalog/entity/Author.java`

Type:

- JPA entity class

Annotations and mapping needs:

- map to the `author` table
- mark the primary key field correctly
- map `open_library_key` as unique and nullable

Fields this entity must have:

- `authorId`
- `openLibraryKey`
- `name`
- `bio`
- `birthDate`
- `photoId`
- `createdAt`
- `updatedAt`

Methods this entity must have:

1. `prePersist()`
2. `preUpdate()`

What the lifecycle methods must do:

- set timestamps before insert and update

Important constraints:

- keep `birthDate` as a string because OpenLibrary date formats are inconsistent
- do not add relationships the schema does not require in this file beyond what the app actually needs

### 10. `src/main/java/com/betterreads/catalog/entity/Book.java`

Type:

- JPA entity class

Annotations and mapping needs:

- map to the `book` table
- map the many-to-many relationship to `Author` through `book_author`
- map the one-to-many relationship to `BookSubject`

Fields this entity must have:

- `bookId`
- `openLibraryWorkKey`
- `title`
- `subtitle`
- `description`
- `coverId`
- `coverUrl`
- `firstPublishYear`
- `isbn`
- `pageCount`
- `language`
- `averageRating`
- `ratingCount`
- `createdAt`
- `updatedAt`
- `authors`
- `subjects`

Methods this entity must have:

1. `prePersist()`
2. `preUpdate()`

What the lifecycle methods must do:

- set timestamps before insert and update

Important constraints:

- `openLibraryWorkKey` is unique and nullable
- map `averageRating` as `BigDecimal`
- keep `authors` lazy
- allow books with zero authors
- map long descriptions cleanly to the text column

What this file must not do:

- no derived business logic about ratings
- no search logic

### 11. `src/main/java/com/betterreads/catalog/entity/BookSubject.java`

Type:

- JPA entity class

Annotations and mapping needs:

- map to the `book_subject` table
- map a many-to-one relationship back to `Book`

Fields this entity must have:

- `bookSubjectId`
- `book`
- `subject`

Methods this entity must have:

- no lifecycle methods are required in this phase

Important constraints:

- do not enforce subject uniqueness in Java because the database does not enforce it
- subject cleanup and deduplication are later concerns

### 12. `src/main/java/com/betterreads/catalog/repository/BookRepository.java`

Type:

- interface extending `JpaRepository<Book, Long>`

This file should contain exactly two derived query methods:

1. `findByOpenLibraryWorkKey(String workKey)`
2. `findByIsbn(String isbn)`

What this file must not do:

- no custom query methods for future recommendation or reporting work
- no business logic

### 13. `src/main/java/com/betterreads/catalog/repository/AuthorRepository.java`

Type:

- interface extending `JpaRepository<Author, Long>`

This file should contain exactly one derived query method:

1. `findByOpenLibraryKey(String key)`

### 14. `src/main/java/com/betterreads/catalog/repository/BookSubjectRepository.java`

Type:

- interface extending `JpaRepository<BookSubject, Long>`

This file should contain exactly one derived query method:

1. `findByBookBookId(Long bookId)`

Why the catalog repository layer exists in Phase 1:

- Phase 2 needs these lookups immediately for search persistence and catalog hydration work

---

## Step 4: auth persistence baseline

Create two files under `src/main/java/com/betterreads/auth/`.

### 15. `src/main/java/com/betterreads/auth/entity/AppUser.java`

Type:

- JPA entity class

Annotations and mapping needs:

- map to the `app_user` table
- keep unique constraints visible for username and email

Fields this entity must have:

- `userId`
- `username`
- `email`
- `passwordHash`
- `displayName`
- `avatarUrl`
- `bio`
- `createdAt`
- `updatedAt`

Methods this entity must have:

1. `prePersist()`
2. `preUpdate()`

What the lifecycle methods must do:

- set timestamps before insert and update

Important constraints:

- `passwordHash` is internal only
- do not expose `passwordHash` in DTOs later
- do not log `passwordHash`
- do not implement `UserDetails` yet

### 16. `src/main/java/com/betterreads/auth/repository/AppUserRepository.java`

Type:

- interface extending `JpaRepository<AppUser, Long>`

This file should contain exactly four derived query methods:

1. `findByUsername(String username)`
2. `findByEmail(String email)`
3. `existsByUsername(String username)`
4. `existsByEmail(String email)`

Why this repository exists now:

- later auth flows need existence checks and lookup methods, but the behavior itself belongs to Phase 4

---

## Step 5: update application configuration

Update `src/main/resources/application.yml`.

This file should contain these configuration sections and values:

- datasource URL using `DB_HOST`, `DB_PORT`, and `DB_NAME` with local defaults for PostgreSQL
- datasource username using `DB_USERNAME` with a local default
- datasource password using `DB_PASSWORD` with a local default
- `spring.flyway.enabled` set to true
- `spring.flyway.locations` pointing to `classpath:db/migration`
- `spring.jpa.hibernate.ddl-auto` set to `validate`
- `openlibrary.base-url` set to `https://openlibrary.org`
- a configurable server port using `SERVER_PORT` with a local default
- conservative logging defaults, with `INFO` as the shared baseline

If the OpenLibrary `User-Agent` needs multiple properties, define them here with clear names rather than hardcoding environment-specific values in Java.

What this file must not contain:

- real secrets
- hardcoded production credentials
- cache TTL settings for this phase

Why `ddl-auto` must be `validate`:

- the project needs startup to fail fast when entity mappings drift away from Flyway migrations

---

## Step 6: what you should have when this phase is complete

New files created in this phase:

1. `src/main/java/com/betterreads/config/SecurityConfig.java`
2. `src/main/java/com/betterreads/config/WebClientConfig.java`
3. `src/main/java/com/betterreads/config/CacheConfig.java`
4. `src/main/java/com/betterreads/config/JacksonConfig.java`
5. `src/main/java/com/betterreads/common/dto/ApiErrorResponse.java`
6. `src/main/java/com/betterreads/common/exception/ResourceNotFoundException.java`
7. `src/main/java/com/betterreads/common/exception/BusinessRuleException.java`
8. `src/main/java/com/betterreads/common/exception/GlobalExceptionHandler.java`
9. `src/main/java/com/betterreads/catalog/entity/Author.java`
10. `src/main/java/com/betterreads/catalog/entity/Book.java`
11. `src/main/java/com/betterreads/catalog/entity/BookSubject.java`
12. `src/main/java/com/betterreads/catalog/repository/BookRepository.java`
13. `src/main/java/com/betterreads/catalog/repository/AuthorRepository.java`
14. `src/main/java/com/betterreads/catalog/repository/BookSubjectRepository.java`
15. `src/main/java/com/betterreads/auth/entity/AppUser.java`
16. `src/main/java/com/betterreads/auth/repository/AppUserRepository.java`

Existing file updated in this phase:

17. `src/main/resources/application.yml`

Minimum method and member surface you should expect:

- 5 bean methods across config classes
- 4 exception handler methods
- 2 exception constructors
- 6 entity lifecycle methods
- 8 repository methods
- entity fields that match the existing schema exactly
- one shared error-response record plus one inner field-error record

---

## Step 7: verification checklist

Before Phase 1 is done, verify all of this:

1. The project compiles.
2. `export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" && ./gradlew check` passes.
3. Flyway applies the migrations against a real PostgreSQL instance.
4. Hibernate validate mode confirms the entities match the schema.
5. Architecture rules still pass.
6. The application starts with the new config beans wired correctly.
7. No entity or configuration class includes logic that really belongs in a service, controller, or integration client.

---

## What comes next

Phase 2 builds the OpenLibrary integration and the first search flow on top of this baseline.

See [full roadmap](../02-backend-architecture-and-roadmap.md#phased-rebuild-plan) for the rest of the sequence.
