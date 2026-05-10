# API reference

Public HTTP endpoints. For the design rationale behind these shapes, see [search design](../explanation/search-design.md).

## Search

`GET /api/search?query=<text>&type=title`

Returns book summaries. No per-result enrichment.

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

## Book detail

`GET /api/books/{workKey}`

Returns metadata for one book: description, subtitle, subjects/genres, editions if needed, review summary, and user-specific collection status when authenticated.

## Trending

`GET /api/books/trending`

Returns a precomputed list. Served from cache or a scheduled sync job, never rebuilt live per request.

## Auth

| Endpoint | Method | Auth |
|---|---|---|
| `/api/v1/auth/register` | POST | none |
| `/api/v1/auth/login` | POST | none |
| `/api/v1/auth/refresh` | POST | `br_refresh` cookie |
| `/api/v1/auth/logout` | POST | `br_refresh` cookie |
| `/api/v1/auth/me` | GET | access JWT |
| `/api/v1/auth/me` | DELETE | access JWT |
| `/api/v1/auth/forgot-password` | POST | none |
| `/api/v1/auth/reset-password` | POST | reset token |
| `/api/v1/auth/verify-email` | POST | verification token |
| `/api/v1/auth/resend-verification` | POST | none |

Access tokens are HS256 JWTs with a 2-hour lifetime, returned in the JSON body and sent on subsequent requests as `Authorization: Bearer`. Refresh tokens are opaque and travel only in the `br_refresh` cookie: `HttpOnly`, `Secure`, `SameSite=Strict`, scoped to `/api/v1/auth`, with a 30-day lifetime. Each successful refresh rotates the cookie value and invalidates the previous one; replaying an already-rotated refresh token revokes every active token for that user. `register` and `login` set the cookie; `logout` clears it. `refresh` and `logout` take no request body.

`forgot-password` returns 204 whether or not the email matches an account so the response cannot be used to enumerate registered users. When the email matches, an outbox row is queued and a worker delivers the reset link asynchronously. `reset-password` consumes the token, replaces the password, and revokes every refresh token for the account so other devices are signed out. Reset tokens are single-use, expire after 15 minutes, and the same opaque 400 covers unknown, expired, and already-consumed tokens.

`register` enqueues a verification mail in the same transaction as the user insert. The response carries `emailVerified: false` until the link is clicked. `verify-email` consumes the token and sets `email_verified_at`. Replay of the same successful token returns 204. A token superseded by a later `resend-verification` returns 400; the account is still unverified and the caller must click the new link. Verification tokens are single-use, expire after 24 hours, and the same opaque 400 covers unknown, expired, and superseded tokens. `resend-verification` returns 204 for unknown emails, for already-verified accounts, and for the success case so the response cannot be used to probe registration or verification state.

`DELETE /me` soft-deletes the authenticated user, revokes every active refresh token, marks any outstanding password-reset and verification tokens consumed, and clears the `br_refresh` cookie. The response is 204. The row stays in `app_user` during a 30-day grace window so the email and username slots remain held; after that, an hourly scheduled sweep removes the row and Postgres foreign keys cascade to the user-scoped tables. Once the soft-delete commits, `GET /me` and `POST /refresh` return 401, login with the same credentials returns 401, and `forgot-password` for the same address enqueues no mail. The endpoint is idempotent: a second `DELETE /me` from the same access JWT also returns 204. The access JWT itself remains valid until natural expiry; because every refresh token has been revoked, no fresh access token can be issued.

Request and response shapes for the auth endpoints are in Swagger UI at `/swagger-ui.html`.
