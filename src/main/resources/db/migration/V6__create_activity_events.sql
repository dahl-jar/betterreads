CREATE TABLE activity_event (
    activity_id    BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT NOT NULL REFERENCES app_user(user_id),
    target_user_id BIGINT,
    book_id        BIGINT REFERENCES book(book_id),
    club_id        BIGINT,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_actor ON activity_event(actor_user_id, created_at DESC);
CREATE INDEX idx_activity_book ON activity_event(book_id);
CREATE INDEX idx_activity_type ON activity_event(event_type);
