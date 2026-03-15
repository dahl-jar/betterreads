CREATE TABLE user_book_interaction (
    interaction_id BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES app_user(user_id),
    book_id        BIGINT NOT NULL REFERENCES book(book_id),
    event_type     VARCHAR(50) NOT NULL,
    event_source   VARCHAR(50),
    weight         NUMERIC(10,2) NOT NULL,
    metadata       JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_book_signal (
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id),
    book_id       BIGINT NOT NULL REFERENCES book(book_id),
    total_weight  NUMERIC(10,2) NOT NULL,
    view_count    INTEGER NOT NULL DEFAULT 0,
    last_event_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, book_id)
);

CREATE TABLE user_recommendation (
    recommendation_id BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES app_user(user_id),
    book_id           BIGINT NOT NULL REFERENCES book(book_id),
    score             NUMERIC(10,4) NOT NULL,
    rank_position     INTEGER NOT NULL,
    reason            VARCHAR(255),
    model_version     VARCHAR(100),
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ,
    UNIQUE (user_id, book_id, model_version)
);

CREATE TABLE similar_book (
    book_id          BIGINT NOT NULL REFERENCES book(book_id),
    similar_book_id  BIGINT NOT NULL REFERENCES book(book_id),
    similarity_score NUMERIC(10,4) NOT NULL,
    reason           VARCHAR(100),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (book_id, similar_book_id)
);

CREATE INDEX idx_interaction_user_time ON user_book_interaction(user_id, created_at DESC);
CREATE INDEX idx_interaction_book_time ON user_book_interaction(book_id, created_at DESC);
CREATE INDEX idx_interaction_event_type ON user_book_interaction(event_type);
CREATE INDEX idx_recommendation_user_rank ON user_recommendation(user_id, rank_position);
CREATE INDEX idx_similar_book_score ON similar_book(book_id, similarity_score DESC);
