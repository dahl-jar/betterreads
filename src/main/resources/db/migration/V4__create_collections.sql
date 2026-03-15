CREATE TABLE user_book_collection (
    collection_id BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    book_id       BIGINT NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    status        VARCHAR(30) NOT NULL,
    started_at    DATE,
    finished_at   DATE,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, book_id)
);

CREATE INDEX idx_collection_user ON user_book_collection(user_id);
CREATE INDEX idx_collection_book ON user_book_collection(book_id);
CREATE INDEX idx_collection_status ON user_book_collection(user_id, status);
