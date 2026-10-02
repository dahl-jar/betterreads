SET LOCAL lock_timeout = '5s';

ALTER TABLE book
    ADD COLUMN metadata_check_requested_at TIMESTAMPTZ;

UPDATE book
SET metadata_check_requested_at = created_at
WHERE metadata_checked_at IS NULL
    AND created_at >= now() - interval '7 days';

CREATE INDEX idx_book_metadata_check_requested ON book (metadata_check_requested_at)
    WHERE metadata_check_requested_at IS NOT NULL;

CREATE TABLE book_series_change (
    change_id  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id    BIGINT      NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    changed_at TIMESTAMPTZ NOT NULL,
    old_series TEXT        NOT NULL,
    new_series TEXT        NOT NULL
);

CREATE INDEX idx_book_series_change_at ON book_series_change (changed_at);

CREATE TABLE series_refresh (
    series_name  TEXT        PRIMARY KEY,
    refreshed_at TIMESTAMPTZ NOT NULL
);
