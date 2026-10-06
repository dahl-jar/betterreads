SET LOCAL lock_timeout = '5s';

ALTER TABLE book
    ADD COLUMN metadata_check_attempts INT NOT NULL DEFAULT 0;

CREATE TABLE book_metadata_change (
    change_id     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id       BIGINT      NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    field         TEXT        NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    source_url    TEXT,
    quote         TEXT,
    check_version INT         NOT NULL,
    changed_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_book_metadata_change_book ON book_metadata_change (book_id, changed_at);
