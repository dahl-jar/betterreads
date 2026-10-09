SET LOCAL lock_timeout = '5s';

ALTER TABLE book
    ADD COLUMN cover_source      VARCHAR(20),
    ADD COLUMN cover_store_url   TEXT,
    ADD COLUMN cover_searched_at TIMESTAMPTZ;

CREATE TABLE cover_blocked (
    url        TEXT        PRIMARY KEY,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
