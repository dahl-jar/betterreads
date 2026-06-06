ALTER TABLE user_book_collection
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
