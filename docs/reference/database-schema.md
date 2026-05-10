# Database schema

## Interactions and recommendations

Collect user behavior from day one so recommendations can start simple and improve later.

### Core interaction table

```sql
create table user_book_interaction (
  interaction_id bigserial primary key,
  user_id bigint not null,
  book_id bigint not null,
  event_type varchar(50) not null,
  event_source varchar(50),
  weight numeric(10,2) not null,
  metadata jsonb,
  created_at timestamptz not null default now(),
  foreign key (user_id) references app_user(user_id) on delete cascade,
  foreign key (book_id) references book(book_id)
);
```

`event_type` values:
- `viewed`, `searched`, `saved`, `want_to_read`, `currently_reading`, `finished`, `dropped`, `rated`, `reviewed`, `commented`, `club_joined`, `club_posted`

`event_source` values:
- `search`, `book_page`, `collection`, `review`, `club`, `feed`

Starter weights:
- viewed = 1.0
- saved = 2.0
- currently_reading = 3.0
- finished = 4.0
- rated_5 = 5.0
- rated_1 = -3.0
- reviewed = 4.0

Weights can be tuned over time.

### Aggregated interaction table

```sql
create table user_book_signal (
  user_id bigint not null,
  book_id bigint not null,
  total_weight numeric(10,2) not null,
  view_count integer not null default 0,
  last_event_at timestamptz not null,
  primary key (user_id, book_id),
  foreign key (user_id) references app_user(user_id) on delete cascade,
  foreign key (book_id) references book(book_id)
);
```

Refreshed by background jobs from raw interaction data.

### Recommendation result table

Stores precomputed recommendations so the Java API only reads and serves them.

```sql
create table user_recommendation (
  recommendation_id bigserial primary key,
  user_id bigint not null,
  book_id bigint not null,
  score numeric(10,4) not null,
  rank_position integer not null,
  reason varchar(255),
  model_version varchar(100),
  generated_at timestamptz not null default now(),
  expires_at timestamptz,
  foreign key (user_id) references app_user(user_id) on delete cascade,
  foreign key (book_id) references book(book_id),
  unique (user_id, book_id, model_version)
);
```

`reason` values:
- `similar_users`, `similar_books`, `same_genre`, `club_activity`, `trending_in_network`, `hybrid_ranked`

### Similar books

```sql
create table similar_book (
  book_id bigint not null,
  similar_book_id bigint not null,
  similarity_score numeric(10,4) not null,
  reason varchar(100),
  generated_at timestamptz not null default now(),
  primary key (book_id, similar_book_id),
  foreign key (book_id) references book(book_id),
  foreign key (similar_book_id) references book(book_id)
);
```

Powers "because you read this", "similar books", and fallback recommendations for new users.

### Activity feed

```sql
create table activity_event (
  activity_id bigserial primary key,
  actor_user_id bigint,
  target_user_id bigint,
  book_id bigint,
  club_id bigint,
  event_type varchar(50) not null,
  payload jsonb,
  created_at timestamptz not null default now(),
  foreign key (actor_user_id) references app_user(user_id) on delete set null,
  foreign key (book_id) references book(book_id)
);
```

Event storage stays separate from rendered feed items. A `feed_item` table can be materialized later if needed. `actor_user_id` is nullable: when the referenced user is hard-deleted, the row is retained and `actor_user_id` is set to `null` instead of the event being cascade-deleted.

## Account deletion and foreign keys

The hourly account-deletion sweep removes soft-deleted users by issuing `DELETE FROM app_user`. The user-scoped tables in this section use `on delete cascade`, so deleting the parent row removes the dependent rows in the same statement. `activity_event` uses `on delete set null` on `actor_user_id`: the row is retained and the actor reference becomes `null`. Auth-side tables (`refresh_token`, `email_token`, `review`, `collection`) also cascade.

### Indexes

```sql
create index idx_user_book_interaction_user_time
  on user_book_interaction(user_id, created_at desc);

create index idx_user_book_interaction_book_time
  on user_book_interaction(book_id, created_at desc);

create index idx_user_book_interaction_event_type
  on user_book_interaction(event_type);

create index idx_user_recommendation_user_rank
  on user_recommendation(user_id, rank_position);

create index idx_similar_book_score
  on similar_book(book_id, similarity_score desc);
```
