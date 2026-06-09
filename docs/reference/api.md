# API reference

Public HTTP endpoints. Request and response shapes for every endpoint are in Swagger UI at `/swagger-ui.html`.

## Search

`GET /api/v1/search/books?q=<text>&offset=0&limit=20`

Full-text search over the catalog, backed by Meilisearch. `q` is required, `offset` defaults to 0, `limit` defaults to 20 and caps at 100. Results are ranked by relevance. Each hit carries the search-level fields below.

```json
{
  "hits": [
    {
      "bookId": "9780441172719",
      "title": "Dune",
      "subtitle": null,
      "seriesName": "Dune",
      "seriesPosition": 1,
      "authors": ["Frank Herbert"],
      "subjects": ["Fiction", "Science fiction"],
      "language": "en",
      "coverUrl": "https://api.betterreadsapp.com/api/v1/images/covers/9780441172719",
      "publicationYear": 1965,
      "popularityScore": 4.21
    }
  ],
  "totalHits": 1,
  "offset": 0,
  "limit": 20
}
```

The first page of a successful search resolves the query's series and author in the background and stages what it finds for a later search. A search run while Meilisearch is down returns an empty page and does not trigger the background work.

## Book detail

`GET /api/v1/books/{key}`

Returns one book. `key` is a source identifier shared with search results (`bookId`). A promoted book is served when it exists, otherwise the staging seed for a book whose enrichment is still running. `complete` says which one came back: `true` for a promoted book, `false` for a seed whose enrichment fields are still null. Returns 404 when neither table has the key.

```json
{
  "key": "9780441172719",
  "complete": true,
  "title": "Dune",
  "subtitle": null,
  "authors": ["Frank Herbert"],
  "description": "...",
  "coverUrl": "https://api.betterreadsapp.com/api/v1/images/covers/9780441172719",
  "firstPublishYear": 1965,
  "isbn": "9780441172719",
  "pageCount": 412,
  "language": "en",
  "averageRating": 4.25,
  "ratingCount": 1200000,
  "seriesName": "Dune",
  "seriesPosition": 1,
  "subjects": ["Fiction", "Science fiction"],
  "awards": ["Hugo Award", "Nebula Award"]
}
```

## Book detail stream

`GET /api/v1/books/{key}/events`

Server-sent events for the same book. A complete book sends one `book-updated` event with the full detail and closes. A seed keeps the stream open until enrichment promotes the book, then sends the filled-in detail as a single `book-updated` event and closes.

## Cover image

`GET /api/v1/images/covers/{key}`

Returns the book's cover image bytes. `key` is the same book key. The image is served from object storage; the first request for an unstored cover downloads the source image, re-encodes it to a size-capped JPEG, stores it, and returns it. Responds with a long-lived `Cache-Control` and an `ETag`, so a matching `If-None-Match` gets 304. Returns 404 when the book has no cover. The book detail and search `coverUrl` point at this endpoint.

## Reviews and community rating

A reader has at most one review per book. Reviews are public reads; writing one needs auth.

- `GET /api/v1/books/{key}/reviews`: a page of reviews for the book.
- `PUT /api/v1/books/{key}/reviews/me`: create or replace the caller's review (rating 1-5, optional text).
- `DELETE /api/v1/books/{key}/reviews/me`: remove the caller's review.
- `GET /api/v1/me/reviews`: the caller's own reviews across books.
- `GET /api/v1/books/{key}/community-rating`: the BetterReads community average, count, and per-star distribution.

The community rating is the average of BetterReads reviews. The source rating (`averageRating`/`ratingCount` on book detail) comes from the external catalogs and is a separate number; a review never changes it. A review write recomputes the community average in the same transaction.

## Comments

Comments thread under a review, with one level of replies.

- `POST /api/v1/books/{key}/comments` and `GET /api/v1/books/{key}/comments`: book-level comments.
- `POST /api/v1/reviews/{reviewId}/comments` and `GET /api/v1/reviews/{reviewId}/comments`: comments on a review.
- `GET /api/v1/comments/{commentId}/replies`: replies to a comment.
- `DELETE /api/v1/comments/{commentId}`: remove the caller's comment.

## Bookshelf

A reader's shelf is their per-book reading state. All shelf endpoints need auth and live under `/api/v1/me/books`.

- `PUT /api/v1/me/books/{key}/status`: set reading status (want to read, currently reading, finished, dropped). Adds the book to the shelf if absent.
- `PUT /api/v1/me/books/{key}/favorite`: mark or unmark a favorite.
- `PATCH /api/v1/me/books/{key}`: update shelf fields (notes, start and finish dates).
- `DELETE /api/v1/me/books/{key}`: remove the book from the shelf.

## Auth

All auth endpoints live under `/api/v1/auth`. A short-lived access token is returned in the JSON body and sent on later requests as `Authorization: Bearer`. A refresh token travels only in the `br_refresh` cookie (`HttpOnly`, `Secure`). Each refresh rotates the cookie and invalidates the previous one; replaying a rotated refresh token revokes the account's active tokens.

`register` and `login` (both POST, no auth) create the session and set the cookie. `refresh` and `logout` (POST, cookie, no body) rotate the cookie or clear it. `GET /me` returns the current user; `DELETE /me` deletes the account.

`forgot-password` and `resend-verification` (POST, no auth) respond the same for any email, matched or not. When the email matches, the mail is queued and delivered by the outbox worker.

`reset-password` and `verify-email` consume a single-use, time-limited token. An unknown, expired, consumed, or superseded token all return the same error.

`DELETE /me` soft-deletes the account, revokes its active tokens, voids any outstanding reset and verification tokens, and clears the cookie. The row holds the email and username slots through a grace window, then a scheduled sweep removes it and the user-scoped tables cascade. After deletion, authenticated reads and refresh fail, and `forgot-password` for the address queues no mail.
