CREATE TABLE pending_book (
    pending_book_id        BIGSERIAL PRIMARY KEY,

    isbn13                 TEXT UNIQUE,
    open_library_work_key  TEXT UNIQUE,
    google_books_volume_id TEXT UNIQUE,
    hardcover_id           TEXT UNIQUE,
    loc_lccn               TEXT UNIQUE,
    wikidata_qid           TEXT UNIQUE,

    title                  TEXT,
    subtitle               TEXT,
    description            TEXT,
    cover_url              TEXT,
    first_publish_year     INTEGER,
    page_count             INTEGER,
    language               TEXT,
    publisher              TEXT,
    average_rating         NUMERIC(3,2),
    rating_count           INTEGER,
    series_name            TEXT,
    series_position        INTEGER,
    subjects               TEXT,
    awards                 TEXT,
    authors                TEXT,

    title_source           TEXT,
    description_source     TEXT,
    cover_source           TEXT,
    publication_year_source TEXT,
    subjects_sources       TEXT,

    status                 TEXT NOT NULL DEFAULT 'PENDING',
    missing_fields         TEXT,
    attempt_count          INTEGER NOT NULL DEFAULT 0,
    first_seen_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at        TIMESTAMPTZ,

    CONSTRAINT pending_book_status_check
        CHECK (status IN ('PENDING', 'PROMOTED', 'INCOMPLETE_FINAL'))
);

CREATE INDEX idx_pending_book_status ON pending_book(status);
