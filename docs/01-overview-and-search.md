# Overview and search

## Goal
Rebuild BetterReads as a cleaner and faster Java backend where OpenLibrary is a data source, not the runtime bottleneck.

## Main problems in v1
- Search is slow because one user search can trigger many OpenLibrary requests.
- The search request asks for too much data up front.
- Controllers, repositories, and services are too tightly mixed.
- Security and session handling are custom instead of using Spring Security properly.
- Tests and configuration are not isolated enough.

## Core idea for v2
Use a two-stage data flow:
1. Fast search returns lightweight book summaries.
2. Book detail loads richer metadata for one selected book.

This means search stays fast even if OpenLibrary is slow.

## How to make search faster without storing everything locally
### 1. Make search a summary endpoint only
Search should return only the fields needed to display a result list:
- title
- author name
- cover id or cover url
- first publish year
- work key
- isbn if already present

Do not fetch these during the search request:
- work description
- edition details
- author details
- subjects/genres from extra endpoints

### 2. Reduce payload size from OpenLibrary
Current search should stop requesting large responses.

Instead of requesting everything, only request the fields you need and use a much smaller limit.

Recommended rules:
- use `limit=10` or `limit=20`
- avoid `fields=*`
- request only explicit fields
- add pagination or load more later

### 3. Do not enrich every result in the request path
A slow pattern is:
- search OpenLibrary
- loop over results
- call `/works/...`
- call `/authors/...`
- call `/editions/...`

That turns one search into many network calls.

In v2:
- search returns immediately after the first search response is mapped
- extra enrichment happens only on detail page or in background jobs

### 4. Cache search queries
Even if data is not fully local, cache helps a lot.

Cache these:
- search query -> search results
- work key -> book details
- isbn -> book details
- author key -> author details
- trending -> weekly list

Recommended first step:
- use `Caffeine`
- 5 to 30 minute TTL for search results
- longer TTL for book details

### 5. Set timeouts and resilience on the HTTP client
The external API should never block the whole app.

Add:
- connect timeout
- read timeout
- retry for safe transient failures only
- circuit breaker if OpenLibrary is unstable

## API design for v2
### Search endpoint
`GET /api/search?query=dune&type=title`

Returns lightweight results only.

Example response shape:
```json
{
  "query": "dune",
  "results": [
    {
      "workKey": "/works/OL123W",
      "title": "Dune",
      "author": "Frank Herbert",
      "coverId": 12345,
      "publicationYear": 1965,
      "isbn": "9780441172719"
    }
  ]
}
```

### Book details endpoint
`GET /api/books/{workKey}`

Returns richer metadata for one selected book:
- description
- subtitle
- subjects/genres
- editions if needed
- review summary
- user-specific collection status later if authenticated

### Trending endpoint
`GET /api/books/trending`

This should come from cache or a scheduled sync job, not rebuilt live per request.

## Search performance rules
For every search request:
- make one OpenLibrary call if cache misses
- map directly to lightweight DTOs
- return results immediately
- do not make per-result detail calls

For every book detail request:
- fetch one work record
- cache it
- merge with local reviews and collection data

## First concrete improvement from v1
If rebuilding from the existing idea, the first big win is simple:
- stop using `fields=*`
- stop requesting 100 results when only 10 are shown
- stop calling work and author endpoints during search

That alone should make the app feel much faster.
