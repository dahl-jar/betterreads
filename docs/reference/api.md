# API reference

Public HTTP endpoints. Request and response shapes for every endpoint are in Swagger UI at `/swagger-ui.html`.

## Search

`GET /api/v1/search/books?q=<text>&offset=0&limit=20`

Full-text search over the catalog, backed by Meilisearch. `q` is required, `offset` defaults to 0, `limit` defaults to 20 and caps at 100. Results come ranked by relevance. Each hit carries only what a result grid needs, so no per-hit detail fetch is required to render it.

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
      "coverUrl": "https://covers.openlibrary.org/b/id/12345-L.jpg",
      "publicationYear": 1965,
      "popularityScore": 4.21
    }
  ],
  "totalHits": 1,
  "offset": 0,
  "limit": 20
}
```

The first page of a successful search also resolves the query's series and author in the background, so an author whose catalog is mostly unstaged fills in for a later search. A search run while Meilisearch is down returns an empty page rather than an error, and does not trigger the background work.

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
  "coverUrl": "https://covers.openlibrary.org/b/id/12345-L.jpg",
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

Server-sent events for the same book. A complete book sends one `book-updated` event with the full detail and closes. A seed keeps the stream open until enrichment promotes the book, then sends the filled-in detail as a single `book-updated` event, so a detail page opened on a cold book patches its missing sections in place without polling.

## Auth

All auth endpoints live under `/api/v1/auth`. Access tokens are HS256 JWTs with a 2-hour lifetime, returned in the JSON body and sent on later requests as `Authorization: Bearer`. Refresh tokens are opaque and travel only in the `br_refresh` cookie: `HttpOnly`, `Secure`, scoped to `/api/v1/auth`, with a 30-day lifetime. Each successful refresh rotates the cookie value and invalidates the previous one. Replaying an already-rotated refresh token revokes every active token for that user.

`register` and `login` (both POST, no auth) create the session and set the cookie. `refresh` and `logout` (POST, `br_refresh` cookie, no body) rotate the cookie or clear it. `GET /me` returns the current user from the access JWT; `DELETE /me` deletes the account.

`forgot-password` (POST, no auth) returns 204 whether or not the email matches an account, so the response cannot enumerate registered users. When the email matches, an outbox row is queued and a worker delivers the reset link. `reset-password` consumes the token, replaces the password, and revokes every refresh token for the account, signing out other devices. Reset tokens are single-use, expire after 15 minutes, and the same opaque 400 covers unknown, expired, and already-consumed tokens.

`register` enqueues a verification mail in the same transaction as the user insert, and the response carries `emailVerified: false` until the link is clicked. `verify-email` consumes the token and sets `email_verified_at`; replaying the same successful token returns 204. A token superseded by a later `resend-verification` returns 400, and the account stays unverified until the new link is clicked. Verification tokens are single-use, expire after 24 hours, and the same opaque 400 covers unknown, expired, and superseded tokens. `resend-verification` returns 204 for unknown emails, already-verified accounts, and the success case alike, so the response cannot probe registration or verification state.

`DELETE /me` soft-deletes the authenticated user, revokes every active refresh token, marks any outstanding password-reset and verification tokens consumed, and clears the `br_refresh` cookie. The response is 204. The row stays in `app_user` during a 30-day grace window so the email and username slots stay held; after that an hourly scheduled sweep removes the row and Postgres foreign keys cascade to the user-scoped tables. Once the soft-delete commits, `GET /me` and `POST /refresh` return 401, login with the same credentials returns 401, and `forgot-password` for the same address enqueues no mail. A second `DELETE /me` from the same access JWT also returns 204. The access JWT stays valid until natural expiry, but because every refresh token is revoked, no fresh access token can be issued.
