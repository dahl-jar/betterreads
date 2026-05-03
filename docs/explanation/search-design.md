# Search design

## Goal

A book tracking app where OpenLibrary is a data source, not a runtime bottleneck.

## Two-stage data flow

1. Search returns book summaries fast.
2. Book detail loads richer metadata for one selected book.

Search stays fast even when OpenLibrary is slow.

## Search rules

### Summary endpoint only

Search returns only the fields needed to display a result list:
- title
- author name
- cover id or cover url
- first publish year
- work key
- isbn if present

Search must not fetch:
- work description
- edition details
- author details
- subjects/genres from extra endpoints

### Reduced payload from OpenLibrary

- `limit=10` or `limit=20`
- explicit `fields=` parameter, never `fields=*`
- pagination or load-more for additional results

### No per-result enrichment

The slow pattern is: search OpenLibrary, loop over results, call `/works/...`, call `/authors/...`, call `/editions/...`. That turns one search into many network calls.

Search returns immediately after the first OpenLibrary response is mapped. Enrichment happens on the detail endpoint or in background jobs.

### Caching

Caffeine, layered by key:

- `query` to search results (5-30 minute TTL)
- `work key` to book details (longer TTL)
- `isbn` to book details
- `author key` to author details
- `trending` to weekly list

### HTTP client resilience

Every WebClient against OpenLibrary configures:
- connect timeout
- read timeout
- retry only for safe transient failures
- circuit breaker if OpenLibrary becomes unstable

## Deduplication and filtering

OpenLibrary returns duplicates and near-duplicates: same book with different work keys, same title with co-authors (translators, editors), multiple editions mixed together.

Deduplication strategy:

- Use the OpenLibrary work key as the primary grouping key. This handles most duplicates.
- Books sharing an ISBN but with different work keys collapse into one.
- When persisting locally, match incoming records against existing ones using `pg_trgm` fuzzy similarity: `similarity(title_a, title_b) > 0.8 AND similarity(author_a, author_b) > 0.7`. Compare against the primary author only.

User-facing filters:

- Language, to cut translations that clutter results.
- Year range, to separate reprints from originals.
- Best-match sort, scoring results by metadata completeness (cover, description, ISBN) to push low-quality records down.

Full duplicate merging and cleanup belongs in background jobs, not in the search request path.

## Genre classification

OpenLibrary subjects are noisy. A single book can carry 40+ subjects mixing real genres with metadata junk ("Protected DAISY", "Accessible book", "Large type books", reading level tags). Raw subjects must not reach the API.

Data model:

- `genre`: curated list seeded from BISAC categories via Flyway. ~100-150 rows.
- `subject_genre_map`: maps raw OpenLibrary subject strings to a genre id. Null genre id means reviewed-and-rejected or not-yet-mapped.
- `book_genre`: links books to their resolved genres. 3-8 genres per book after cleanup.

Flow:

1. Book persisted with raw subjects.
2. Each subject looked up in `subject_genre_map` by exact string match.
3. Matched subjects link the book to those genres.
4. Unmatched subjects inserted with null genre id for review.
5. Admin endpoint surfaces unmapped subjects sorted by frequency.

Keyword matching as a first-pass automation: if a raw subject contains a known genre keyword (e.g. "science fiction"), auto-map it.

ML-assisted classification, deferred: embed genre names and incoming subjects with a small embedding model, find closest genre by cosine similarity, propose mappings for admin review.

Anti-patterns:

- Don't expose raw OpenLibrary subjects to users.
- Don't use fuzzy thresholds as the primary classification path; too unpredictable.
- Don't pre-map every subject. Let the map build organically.

## Search performance rules

Every search request:
- one OpenLibrary call on cache miss
- map to summary DTOs
- return immediately
- no per-result detail calls

Every book detail request:
- one work record fetch
- cache result
- merge with local reviews and collection data
