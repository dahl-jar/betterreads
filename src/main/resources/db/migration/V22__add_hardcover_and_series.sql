ALTER TABLE book
    ADD COLUMN hardcover_id    TEXT,
    ADD COLUMN series_name     TEXT,
    ADD COLUMN series_position INTEGER,
    ADD CONSTRAINT book_hardcover_id_key UNIQUE (hardcover_id);
