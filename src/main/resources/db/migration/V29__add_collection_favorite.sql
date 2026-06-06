ALTER TABLE user_book_collection
    ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_collection_favorite ON user_book_collection(user_id) WHERE favorite;
