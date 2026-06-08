CREATE TABLE comment (
    comment_id        BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    target_type       VARCHAR(16) NOT NULL CHECK (target_type IN ('BOOK', 'REVIEW')),
    target_id         BIGINT NOT NULL,
    parent_comment_id BIGINT REFERENCES comment(comment_id) ON DELETE CASCADE,
    body              TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_target ON comment(target_type, target_id);
CREATE INDEX idx_comment_parent ON comment(parent_comment_id);
