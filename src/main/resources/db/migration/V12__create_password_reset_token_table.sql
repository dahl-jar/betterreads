CREATE TABLE password_reset_token (
    password_reset_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash              TEXT NOT NULL UNIQUE,
    issued_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ NOT NULL,
    consumed_at             TIMESTAMPTZ,
    CONSTRAINT chk_password_reset_token_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE INDEX idx_password_reset_token_user_unconsumed
    ON password_reset_token (user_id) WHERE consumed_at IS NULL;
