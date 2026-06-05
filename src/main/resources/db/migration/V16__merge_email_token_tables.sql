CREATE TABLE email_token (
    email_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    purpose        TEXT NOT NULL,
    token_hash     TEXT NOT NULL UNIQUE,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ NOT NULL,
    consumed_at    TIMESTAMPTZ,
    CONSTRAINT chk_email_token_purpose
        CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION')),
    CONSTRAINT chk_email_token_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX idx_email_token_active_password_reset
    ON email_token (user_id)
    WHERE consumed_at IS NULL AND purpose = 'PASSWORD_RESET';

CREATE UNIQUE INDEX idx_email_token_active_email_verification
    ON email_token (user_id)
    WHERE consumed_at IS NULL AND purpose = 'EMAIL_VERIFICATION';

INSERT INTO email_token
    (user_id, purpose, token_hash, issued_at, expires_at, consumed_at)
SELECT user_id, 'PASSWORD_RESET', token_hash, issued_at, expires_at, consumed_at
FROM password_reset_token;

INSERT INTO email_token
    (user_id, purpose, token_hash, issued_at, expires_at, consumed_at)
SELECT user_id, 'EMAIL_VERIFICATION', token_hash, issued_at, expires_at, consumed_at
FROM email_verification_token;

-- The old tables are kept for one deploy cycle so the documented rollback path
-- (mv app.jar.previous app.jar) can boot the previous jar, which still maps the
-- legacy entities and would otherwise fail Hibernate ddl-auto: validate.
-- A later migration drops them once the new jar is verified live.
