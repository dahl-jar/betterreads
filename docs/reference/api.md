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
| `/api/v1/auth/refresh` | POST | refresh token in body |
| `/api/v1/auth/logout` | POST | refresh token in body |
| `/api/v1/auth/me` | GET | access JWT |

Access tokens are HS256 JWTs with a 2-hour lifetime. Refresh tokens are opaque, rotated on every refresh, and stored as HMAC-SHA256 hashes server-side. Replaying an already-rotated refresh token revokes every active token for that user.

Request and response shapes for the auth endpoints are in Swagger UI at `/swagger-ui.html`.
