ALTER TABLE book
    ADD COLUMN google_books_volume_id TEXT,
    ADD CONSTRAINT book_google_books_volume_id_key UNIQUE (google_books_volume_id);
