CREATE TABLE review (
    review_id  BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    book_id    BIGINT NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    rating     INTEGER CHECK (rating >= 1 AND rating <= 5),
    title      VARCHAR(255),
    body       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, book_id)
);

CREATE INDEX idx_review_book ON review(book_id);
CREATE INDEX idx_review_user ON review(user_id);
