# Database schema

PostgreSQL 17. The schema is managed by Flyway migrations under `src/main/resources/db/migration/`; this page describes the current state after the latest migration. All primary keys are `bigint` identity columns. All timestamps are `timestamptz`.

## Users

### app_user

```sql
user_id           bigserial primary key,
username          varchar(50) not null unique,
email             text not null unique,
password_hash     text not null,
display_name      varchar(100),
avatar_url        text,
bio               text,
email_verified_at timestamptz,
deleted_at        timestamptz,
created_at        timestamptz not null default now(),
updated_at        timestamptz not null default now()
```

`password_hash` is a BCrypt hash, length-checked at 60. `email` is length-capped at 254. `deleted_at` set marks a soft-deleted account during its grace window; `email_verified_at` set marks a confirmed email.

## Catalog

### book

```sql
book_id                bigserial primary key,
title                  text not null,
subtitle               text,
description            text,
cover_id               integer,
cover_url              text,
first_publish_year     integer,
isbn                   varchar(20),
page_count             integer,
language               varchar(10),
average_rating         numeric(3,2),
rating_count           integer default 0,
series_name            text,
series_position        integer,
open_library_work_key  text unique,
google_books_volume_id text unique,
hardcover_id           text unique,
loc_lccn               text unique,
wikidata_qid           text unique,
dedup_key              text not null unique,
created_at             timestamptz not null default now(),
updated_at             timestamptz not null default now()
```

Each external source has its own unique id column. `dedup_key` coalesces them (ISBN, OpenLibrary work key, Google Books volume id, Hardcover id, LoC LCCN, Wikidata QID) into one stable key. `isbn` is constrained to null or 10/13 digits without hyphens. A GIN trigram index on `title` backs fuzzy lookups.

### author

```sql
author_id        bigserial primary key,
name             text not null unique,
open_library_key text unique,
wikidata_qid     text unique,
bio              text,
birth_date       varchar(50),
photo_id         integer,
photo_url        text,
created_at       timestamptz not null default now(),
updated_at       timestamptz not null default now()
```

A GIN trigram index on `name` backs author-name matching.

### book_author, book_subject, book_award

```sql
book_author  (book_id, author_id)  primary key (book_id, author_id)
book_subject (book_subject_id, book_id, subject)
book_award   (book_award_id, book_id, award)
```

All three reference `book(book_id)` with `on delete cascade`. `book_author` also references `author(author_id)` with `on delete cascade`.

### pending_book

Staging table for books that have not yet met the show bar. Carries the same external id columns and metadata as `book`, plus per-field source columns (`title_source`, `description_source`, `cover_source`, `publication_year_source`, `subjects_sources`) and staging state:

```sql
status          text not null default 'PENDING',  -- PENDING | PROMOTED | INCOMPLETE_FINAL
missing_fields  text,
attempt_count   integer not null default 0,
first_seen_at   timestamptz not null default now(),
last_attempt_at timestamptz,
dedup_key       text not null unique
```

A row is keyed by `dedup_key` and promoted into `book` once it carries every required field.

## Authentication

### refresh_token

```sql
refresh_token_id bigint generated always as identity primary key,
user_id          bigint not null references app_user(user_id) on delete cascade,
token_hash       text not null unique,
issued_at        timestamptz not null default now(),
expires_at       timestamptz not null,
revoked_at       timestamptz,
replaced_by      bigint references refresh_token(refresh_token_id) on delete set null
```

Only the HMAC-SHA256 hash is stored. `replaced_by` links a rotated token to its successor. A partial index covers active tokens (`where revoked_at is null`).

### email_token

```sql
email_token_id bigint generated always as identity primary key,
user_id        bigint not null references app_user(user_id) on delete cascade,
purpose        text not null,  -- PASSWORD_RESET | EMAIL_VERIFICATION
token_hash     text not null unique,
issued_at      timestamptz not null default now(),
expires_at     timestamptz not null,
consumed_at    timestamptz
```

One table for both password-reset and email-verification tokens. Two partial unique indexes hold at most one active token per user per purpose (`where consumed_at is null`).

### mail_outbox

```sql
mail_outbox_id  bigint generated always as identity primary key,
template        text not null,  -- password_reset | email_verification
recipient       text not null,
payload         jsonb not null,
created_at      timestamptz not null default now(),
sent_at         timestamptz,
failed_at       timestamptz,
attempt_count   int not null default 0,
next_attempt_at timestamptz not null default now(),
last_error      text
```

A row is enqueued in the same transaction as the user action and delivered by the outbox worker. A partial index covers pending rows (`where sent_at is null and failed_at is null`).

## Account deletion

`app_user.deleted_at` marks a soft delete. The hourly sweep hard-deletes rows past the grace window with `DELETE FROM app_user`. User-scoped tables cascade: `refresh_token`, `email_token`, `review`, `user_book_collection`, `user_book_interaction`, `user_book_signal`, `user_recommendation`. `activity_event.actor_user_id` uses `on delete set null`, retaining the event with a null actor.

## Tables not yet wired to a feature

Earlier migrations (V3-V6) created tables for reviews, collections, interactions, recommendations, similar books, and an activity feed. They exist in the database but no shipped code reads or writes them yet:

- `review` (user_id, book_id, rating, title, body), unique per user and book
- `user_book_collection` (user_id, book_id, status, started_at, finished_at, notes), unique per user and book
- `user_book_interaction` (user_id, book_id, event_type, weight, metadata)
- `user_book_signal` (user_id, book_id, total_weight, view_count, last_event_at)
- `user_recommendation` (user_id, book_id, score, rank_position, reason, model_version)
- `similar_book` (book_id, similar_book_id, similarity_score, reason)
- `activity_event` (actor_user_id, target_user_id, book_id, event_type, payload)
