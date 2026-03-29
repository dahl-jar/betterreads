# Current phase: Phase 1 - persistence and platform baseline

## What exists

- Spring Boot project with quality gate tooling configured
- Flyway migrations V1 through V6 covering users, books, authors, reviews, collections, interactions, recommendations, and activity events
- Feature package folders under `src/main/java/com/betterreads/`
- ArchUnit rules for layered architecture
- No Java classes beyond `BetterReadsApplication.java`

## What this phase delivers

Phase 1 makes the backend real. By the end, the application boots against PostgreSQL, Flyway owns the schema, shared configuration is in place, shared API error handling works, and the first catalog and auth persistence classes are ready for Phase 2.

Each section below says which file to create, what goes inside it, and what to leave out. It also explains the concepts behind each piece so you can write the code with an understanding of what it does and why.

## Scope

Build now:

- shared Spring configuration
- shared API error handling
- catalog entities and repositories
- auth user entity and repository
- application YAML configuration

Don't build yet:

- search controllers or services
- OpenLibrary client implementation
- book detail enrichment
- review, collection, recommendation, or activity-feed Java code
- authentication flows, JWT handling, or endpoint authorization rules

The database already has tables for later phases. That doesn't mean the Java code for those phases belongs here.

## Design rules for every file in this phase

- Match the Flyway schema exactly. Hibernate validates in this phase; it does not generate the schema.
- Keep layering clean: `controller -> service -> repository`. This phase stops before controllers and services, so don't invent shortcuts around that later structure.
- Keep each class small and single-purpose.
- Constructor injection only where dependencies exist.
- Don't expose entities directly to future API responses.
- Don't add speculative methods just because they might be useful later.

### SOLID focus

- SRP: each config class owns one area of framework setup, each entity maps one table.
- OCP: configure shared infrastructure so later phases can extend it without replacing the baseline.
- ISP: repository interfaces expose only the queries needed now and in the immediate next phase.
- DIP: later features should consume Spring-managed beans and repository interfaces, not concrete hacks.

---

## Step 1: shared Spring configuration [DONE]

Create four files in `src/main/java/com/betterreads/config/`.

### 1. SecurityConfig.java

`src/main/java/com/betterreads/config/SecurityConfig.java`

A `@Configuration` class with `@EnableWebSecurity`. Two `@Bean` methods.

#### How Spring Security configuration works

Spring Security has its own filter machinery built in. You don't write filters yourself. Instead, you write a configuration class that hands Spring a `SecurityFilterChain` bean. Spring calls your bean method at startup, passes you an `HttpSecurity` builder, and you configure your rules on that builder. When you're done, you call `http.build()` and return the result. Spring takes that object and wires it into its internal filter pipeline.

`@EnableWebSecurity` turns on the filter infrastructure. `@Configuration` tells Spring "this class contains beans." The combination means Spring will find your `SecurityFilterChain` bean method and use it to configure security.

#### securityFilterChain bean method

This method takes `HttpSecurity` as a parameter (Spring injects it) and returns `SecurityFilterChain`.

The `HttpSecurity` builder uses a lambda DSL. Each concern (CSRF, session management, authorization rules) gets configured through a separate chained method call with a lambda. For example, to disable CSRF you call the csrf configuration method and pass a lambda that disables it. Same pattern for session management and authorization.

What to configure:

- **Authorization rules:** permit all requests for now. The builder has an `authorizeHttpRequests` method where you set matchers. For this phase, just let everything through. Add a TODO comment that Phase 4 replaces permit-all with real endpoint rules.
- **CSRF:** disable it. CSRF protection is for browser-based session cookies. Since this API will use stateless JWT auth, CSRF doesn't apply. The builder has a csrf configuration method.
- **Session management:** set the session creation policy to `STATELESS`. This tells Spring not to create HTTP sessions. Spring Security's `SessionCreationPolicy` enum has this value. You set it through the session management configuration on the builder.

#### passwordEncoder bean method

Returns a `PasswordEncoder`. Use Spring Security's `BCryptPasswordEncoder` with default strength. This bean exists now so that when the auth service is built later, it can inject the encoder directly instead of having to create one inline. BCrypt is the standard choice for password hashing in Spring Security apps.

#### What this file must not do

- No JWT parsing, custom auth providers, user lookup logic, or controller/repository access

---

### 2. WebClientConfig.java

`src/main/java/com/betterreads/config/WebClientConfig.java`

A `@Configuration` class. One `@Bean` method.

#### How WebClient beans work

`WebClient` is Spring's non-blocking HTTP client. You can create one with `WebClient.builder()`, configure it with a base URL, default headers, and timeouts, then call `.build()`. When you expose that as a `@Bean`, any other class in the application can inject it.

Since this project might eventually have multiple HTTP clients (for different external APIs), the bean should be clearly identifiable as the OpenLibrary client. You can do this with a `@Qualifier` annotation or a specific bean name, so later code can inject exactly this client and not some other one.

#### openLibraryWebClient bean method

What to configure:

- **Base URL:** read from `openlibrary.base-url` in application config. Inject it with `@Value` on a constructor parameter or use `@ConfigurationProperties`.
- **Connect timeout:** 5000 ms. This is how long the client waits to establish a TCP connection. Configure this through the underlying `HttpClient` that backs the WebClient. You create a `HttpClient` with a connection timeout, then build a `ClientHttpConnector` from it, then pass that connector to the WebClient builder.
- **Read timeout:** 10000 ms. This is how long the client waits for data after the connection is open. Configure this as a response timeout on the `HttpClient`.
- **User-Agent header:** set a default header. OpenLibrary's API guidelines ask callers to identify themselves. Don't hardcode contact info; make it configuration-driven if needed. The WebClient builder has a `defaultHeader` method.

Use private constants for the timeout values instead of magic numbers.

#### What this file must not do

- No HTTP request methods for specific endpoints, no DTO mapping, no retry logic, no caching logic. Those belong in the integration layer, not the config.

---

### 3. CacheConfig.java

`src/main/java/com/betterreads/config/CacheConfig.java`

A `@Configuration` class with `@EnableCaching`. One `@Bean` method.

#### How Spring caching works

Spring's `@EnableCaching` turns on annotation-driven caching. Once enabled, you can put `@Cacheable("cacheName")` on a service method, and Spring intercepts calls to that method, checks the named cache, and returns the cached value if there's a hit. The cache manager bean you define here decides what backing implementation those caches use and how they behave.

Caffeine is the cache library already in the project's dependencies. It's an in-memory cache. You configure it by creating `CaffeineCacheManager` or by building individual `Cache` instances with Caffeine's builder and registering them with a `SimpleCacheManager`.

#### cacheManager bean method

Register three named caches, each with its own TTL (time-to-live) and maximum entry count:

- `searchResults`: 15 minutes, max 500 entries
- `bookDetails`: 60 minutes, max 1000 entries
- `authorDetails`: 60 minutes, max 500 entries

Caffeine's builder lets you set `expireAfterWrite(duration)` for TTL and `maximumSize(count)` for the entry cap. Create one Caffeine-backed cache per name, put them in a list, and give that list to a `SimpleCacheManager`.

You could also use `CaffeineCacheManager`, but `SimpleCacheManager` gives you per-cache control without any extra setup.

#### What this file must not do

- No YAML-driven cache specs, no manual eviction logic, no business-specific invalidation rules

---

### 4. JacksonConfig.java

`src/main/java/com/betterreads/config/JacksonConfig.java`

A `@Configuration` class. One `@Bean` method.

#### How Jackson configuration works

Jackson is the JSON library Spring Boot uses to serialize and deserialize request/response bodies. Spring Boot auto-configures an `ObjectMapper`, but you can customize it by providing your own bean or by providing a `Jackson2ObjectMapperBuilderCustomizer` bean that adjusts the auto-configured one.

Either approach works. The customizer approach is slightly cleaner because it layers on top of Spring Boot's defaults instead of replacing them entirely, but providing your own `ObjectMapper` bean is simpler to reason about.

#### What the bean must configure

- **Unknown properties:** don't fail when JSON has fields the Java class doesn't have. This matters because OpenLibrary payloads change and include fields we don't map. Jackson's `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` controls this.
- **Java time support:** register the `JavaTimeModule` so `Instant`, `LocalDateTime`, etc. serialize properly. Without this module, Jackson doesn't know how to handle `java.time` types.
- **Date format:** serialize dates as ISO-8601 strings, not Unix timestamps. Jackson's `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` controls this (disable it).
- **Null handling:** omit null fields from JSON output. Jackson's `JsonInclude.Include.NON_NULL` does this, set through the serialization inclusion setting.

#### What this file must not do

- No feature-specific serializers, no OpenLibrary DTO logic, no controller response shaping

---

## Step 2: shared error handling [DONE]

Create four files under `src/main/java/com/betterreads/common/`.

### 5. ApiErrorResponse.java

`src/main/java/com/betterreads/common/dto/ApiErrorResponse.java`

#### What a Java record is

A `record` is a compact class that holds immutable data. You declare the fields in the header, and Java generates the constructor, getters, `equals`, `hashCode`, and `toString` for you. Records are ideal for DTOs because they're just data with no behavior.

#### What to build

A top-level `ApiErrorResponse` record with fields: `status` (int), `message` (String), `timestamp` (Instant), and `fieldErrors` (a List of field-error records).

Inside `ApiErrorResponse`, define a `static` inner record for individual field-level validation errors, with `field` (String) and `message` (String).

`fieldErrors` should be absent (null or empty list) for non-validation failures. Since we configured Jackson to omit nulls, passing null here means the field just won't appear in the JSON for 404s, 409s, and 500s.

---

### 6. ResourceNotFoundException.java

`src/main/java/com/betterreads/common/exception/ResourceNotFoundException.java`

A class extending `RuntimeException`. One constructor that takes a resource name (like "Book") and an identifier description (like "workKey: /works/OL123W"), and passes a formatted message to the `super()` constructor. The message reads something like "Book not found with workKey: /works/OL123W".

The exception itself has no HTTP awareness. It's just a domain exception. The exception handler (below) decides the HTTP status.

---

### 7. BusinessRuleException.java

`src/main/java/com/betterreads/common/exception/BusinessRuleException.java`

A class extending `RuntimeException`. One constructor that takes a message string and passes it to `super()`. Used for things like "user already reviewed this book" or "book already in collection" in later phases.

Same pattern: no HTTP status inside the exception. The handler decides.

---

### 8. GlobalExceptionHandler.java

`src/main/java/com/betterreads/common/exception/GlobalExceptionHandler.java`

A `@RestControllerAdvice` class with four handler methods.

#### How @RestControllerAdvice works

`@RestControllerAdvice` is a class-level annotation that tells Spring "this class handles exceptions thrown by any controller." Each method in the class is annotated with `@ExceptionHandler(SomeException.class)` and handles that specific exception type. The method returns a response body (in our case, an `ApiErrorResponse`), and you annotate the method with `@ResponseStatus` or return a `ResponseEntity` to set the HTTP status code.

Spring matches thrown exceptions to the most specific handler it can find. If a controller throws `ResourceNotFoundException`, it lands in the not-found handler. If nothing specific matches, it falls through to the generic `Exception` handler.

#### The four handlers

**Validation errors** (`MethodArgumentNotValidException`): return HTTP 400. This exception is thrown automatically by Spring when a `@Valid` request body fails Bean Validation. The exception object contains a `BindingResult` with all the field errors. Loop through them, build a list of field-error inner records, and return an `ApiErrorResponse` with that list populated. Log at WARN.

**Not found** (`ResourceNotFoundException`): return HTTP 404. Build an `ApiErrorResponse` using the exception's message. No field errors. Log at WARN.

**Business rule violation** (`BusinessRuleException`): return HTTP 409. Same pattern as not-found but with a different status. Log at WARN.

**Catch-all** (`Exception`): return HTTP 500. Return the message "An unexpected error occurred" -- never expose the actual exception message in the response body, because it could contain internal details. Log at ERROR with the full stack trace.

Logging rules: 4xx at WARN with concise messages, 500 at ERROR with stack traces. Never log passwords, tokens, or API keys.

---

## Step 3: catalog persistence baseline

Create six files under `src/main/java/com/betterreads/catalog/`.

### How JPA entities work

A JPA entity is a Java class that maps to a database table. You annotate the class with `@Entity` and `@Table(name = "table_name")`. Each field maps to a column. JPA uses the field name by default, converting camelCase to snake_case (Spring Boot's default naming strategy handles this), but you can override with `@Column(name = "column_name")` when needed.

The primary key field gets `@Id`. For auto-incrementing keys (like `BIGSERIAL` in Postgres), you add `@GeneratedValue(strategy = GenerationType.IDENTITY)`, which tells JPA to let the database generate the value.

`@PrePersist` and `@PreUpdate` are lifecycle callbacks. A method annotated with `@PrePersist` runs just before the entity is first inserted. `@PreUpdate` runs before every update. We use these to set `createdAt` and `updatedAt` timestamps.

Since `ddl-auto` is set to `validate`, Hibernate checks at startup that every entity field matches a real column in the database. If the entity has a field that doesn't exist in the table, or the types don't match, the application refuses to start. This is a safety net: the Flyway migrations are the source of truth for the schema, and the entities must match.

### How Spring Data repositories work

A repository interface extends `JpaRepository<EntityType, IdType>`. You don't write an implementation. Spring generates one at startup. You get standard CRUD methods for free (`save`, `findById`, `findAll`, `delete`, etc.).

For custom lookups, you write "derived query methods" -- method signatures that Spring parses to generate SQL. `findByOpenLibraryWorkKey(String workKey)` becomes a `SELECT ... WHERE open_library_work_key = ?` query. `existsByEmail(String email)` becomes a `SELECT EXISTS(... WHERE email = ?)` query. Spring handles the naming convention: it splits the method name by camelCase, maps each part to entity fields, and builds the query.

These methods return `Optional<Entity>` for single-result lookups, `List<Entity>` for multi-result queries, and `boolean` for existence checks.

---

### 9. Author.java

`src/main/java/com/betterreads/catalog/entity/Author.java`

Map to the `author` table. The table definition from V2 migration:

- `author_id BIGSERIAL PRIMARY KEY`
- `open_library_key VARCHAR(100) UNIQUE` -- nullable
- `name VARCHAR(255) NOT NULL`
- `bio TEXT`
- `birth_date VARCHAR(50)` -- string, not a date type, because OpenLibrary formats are inconsistent
- `photo_id INTEGER`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

Fields: `authorId`, `openLibraryKey`, `name`, `bio`, `birthDate`, `photoId`, `createdAt`, `updatedAt`.

Mark `openLibraryKey` with a unique constraint annotation. Keep `birthDate` as a String.

Add `prePersist()` and `preUpdate()` lifecycle methods for timestamps.

Don't add relationship mappings here. The `book_author` join table is mapped from the `Book` side.

---

### 10. Book.java

`src/main/java/com/betterreads/catalog/entity/Book.java`

Map to the `book` table. The table definition from V2 migration:

- `book_id BIGSERIAL PRIMARY KEY`
- `open_library_work_key VARCHAR(100) UNIQUE` -- nullable
- `title VARCHAR(500) NOT NULL`
- `subtitle VARCHAR(500)`
- `description TEXT`
- `cover_id INTEGER`
- `cover_url VARCHAR(500)`
- `first_publish_year INTEGER`
- `isbn VARCHAR(20)`
- `page_count INTEGER`
- `language VARCHAR(10)`
- `average_rating NUMERIC(3,2) DEFAULT 0.0`
- `rating_count INTEGER DEFAULT 0`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

Fields: `bookId`, `openLibraryWorkKey`, `title`, `subtitle`, `description`, `coverId`, `coverUrl`, `firstPublishYear`, `isbn`, `pageCount`, `language`, `averageRating`, `ratingCount`, `createdAt`, `updatedAt`, plus `authors` and `subjects`.

Map `averageRating` as `BigDecimal` to match `NUMERIC(3,2)`.

#### Relationship: authors (many-to-many)

The `book_author` join table has `book_id` and `author_id` as a composite primary key. To map this in JPA, use `@ManyToMany` with a `@JoinTable` annotation on the `authors` field. The `@JoinTable` specifies the join table name (`book_author`), the join column (`book_id`), and the inverse join column (`author_id`).

Set fetch type to `LAZY`. Lazy means JPA doesn't load the authors from the database until you actually access the `authors` field. This avoids unnecessary queries when you only need the book's own fields.

Books can have zero authors, so the collection should be initialized to an empty list.

#### Relationship: subjects (one-to-many)

`BookSubject` has a `book_id` foreign key. Map this with `@OneToMany(mappedBy = "book")` on the `subjects` field. The `mappedBy` value is the field name in `BookSubject` that owns the relationship.

Add `prePersist()` and `preUpdate()` lifecycle methods for timestamps.

---

### 11. BookSubject.java

`src/main/java/com/betterreads/catalog/entity/BookSubject.java`

Map to the `book_subject` table. Fields: `bookSubjectId`, `book`, `subject`.

Map the `book` field with `@ManyToOne` and `@JoinColumn(name = "book_id")`. This is the owning side of the one-to-many relationship declared in `Book.java`.

Don't enforce subject uniqueness in Java. The database doesn't enforce it, and subject cleanup is a later concern.

No lifecycle methods needed.

---

### 12-14. Repositories

**BookRepository** -- `JpaRepository<Book, Long>`:
- `findByOpenLibraryWorkKey(String workKey)`
- `findByIsbn(String isbn)`

**AuthorRepository** -- `JpaRepository<Author, Long>`:
- `findByOpenLibraryKey(String key)`

**BookSubjectRepository** -- `JpaRepository<BookSubject, Long>`:
- `findByBookBookId(Long bookId)` -- note the double "Book": Spring parses this as "navigate to the `book` field, then to `bookId`"

No custom queries, no business logic. Just these lookup methods.

---

## Step 4: auth persistence baseline

Create two files under `src/main/java/com/betterreads/auth/`.

### 15. AppUser.java

`src/main/java/com/betterreads/auth/entity/AppUser.java`

Map to the `app_user` table. The V1 migration:

- `user_id BIGSERIAL PRIMARY KEY`
- `username VARCHAR(50) NOT NULL UNIQUE`
- `email VARCHAR(255) NOT NULL UNIQUE`
- `password_hash VARCHAR(255) NOT NULL`
- `display_name VARCHAR(100)`
- `avatar_url VARCHAR(500)`
- `bio TEXT`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

Fields: `userId`, `username`, `email`, `passwordHash`, `displayName`, `avatarUrl`, `bio`, `createdAt`, `updatedAt`.

Add unique constraint annotations on `username` and `email`.

Add `prePersist()` and `preUpdate()` lifecycle methods for timestamps.

`passwordHash` is internal only. Don't expose it in DTOs later, don't log it. Don't implement Spring Security's `UserDetails` interface yet -- that belongs in Phase 4 when the auth flow is built.

### 16. AppUserRepository.java

`src/main/java/com/betterreads/auth/repository/AppUserRepository.java`

`JpaRepository<AppUser, Long>` with four derived methods:

- `findByUsername(String username)`
- `findByEmail(String email)`
- `existsByUsername(String username)`
- `existsByEmail(String email)`

The auth service will need existence checks for registration (is this username taken?) and lookup methods for login. The methods exist now; the actual auth behavior is Phase 4.

---

## Step 5: update application configuration

Update `src/main/resources/application.yml`.

#### How Spring Boot configuration works

`application.yml` is the central config file. Spring Boot reads it at startup and binds values to framework settings and custom properties. You can reference environment variables with `${ENV_VAR:default}` syntax, where the part after the colon is the fallback value used when the env var isn't set.

What to configure:

- **Datasource:** `spring.datasource.url` using `${DB_HOST:localhost}`, `${DB_PORT:5432}`, and `${DB_NAME:betterreads}` interpolated into a JDBC URL. Username and password from `${DB_USERNAME:postgres}` and `${DB_PASSWORD:postgres}`.
- **Flyway:** `spring.flyway.enabled: true`, `spring.flyway.locations: classpath:db/migration`
- **JPA:** `spring.jpa.hibernate.ddl-auto: validate` -- Hibernate checks entity-to-table mappings at startup but doesn't create or modify tables. Flyway owns the schema.
- **OpenLibrary:** `openlibrary.base-url: https://openlibrary.org` (custom property, read by `WebClientConfig`)
- **Server port:** `server.port: ${SERVER_PORT:8080}`
- **Logging:** INFO as the baseline. Don't go more granular unless debugging a specific area.

If the OpenLibrary `User-Agent` needs configuration properties (app name, contact), define them here with clear names.

Don't put real secrets, hardcoded production credentials, or cache TTL settings in this file.

`ddl-auto: validate` is deliberate. If an entity field doesn't match the Flyway schema, the app fails to start. You find out immediately instead of discovering drift later.

---

## Step 6: what you should have when this phase is complete

New files:

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

Updated:

17. `src/main/resources/application.yml`

Expected surface:

- 5 bean methods across config classes
- 4 exception handler methods
- 2 exception constructors
- 6 entity lifecycle methods
- 8 repository methods
- entity fields matching the existing schema exactly
- one shared error-response record with one inner field-error record

---

## Step 7: verification checklist

Before Phase 1 is done:

1. The project compiles.
2. `export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" && ./gradlew check` passes.
3. Flyway applies migrations against a real PostgreSQL instance.
4. Hibernate validate mode confirms entities match the schema.
5. Architecture rules still pass.
6. The application starts with the new config beans wired correctly.
7. No entity or configuration class includes logic that belongs in a service, controller, or integration client.

---

## What comes next

Phase 2 builds the OpenLibrary integration and the first search flow on top of this baseline.

See [full roadmap](../02-backend-architecture-and-roadmap.md#phased-rebuild-plan) for the rest of the sequence.
