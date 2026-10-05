SET LOCAL lock_timeout = '5s';

ALTER TABLE author
    ADD COLUMN name_key TEXT;

ALTER TABLE book_author
    ADD COLUMN role TEXT NOT NULL DEFAULT 'AUTHOR',
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_book_author_role
        CHECK (role IN ('AUTHOR', 'EDITOR', 'ILLUSTRATOR', 'TRANSLATOR', 'NARRATOR', 'INTRODUCTION', 'OTHER'));

ALTER TABLE book
    ADD COLUMN credits_checked_at TIMESTAMPTZ;

CREATE TABLE author_merge (
    merged_author_id BIGINT PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES author (author_id) ON DELETE CASCADE,
    merged_name TEXT NOT NULL
);

CREATE INDEX ix_author_merge_author_id ON author_merge (author_id);

CREATE INDEX ix_book_author_author_id ON book_author (author_id);
