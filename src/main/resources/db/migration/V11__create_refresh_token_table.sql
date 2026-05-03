CREATE TABLE refresh_token (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash       TEXT NOT NULL UNIQUE,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by      BIGINT REFERENCES refresh_token(refresh_token_id) ON DELETE SET NULL,
    CONSTRAINT chk_refresh_token_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_token_user_active
    ON refresh_token (user_id) WHERE revoked_at IS NULL;
