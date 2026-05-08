ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_verification_token (
    email_verification_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash                  TEXT NOT NULL UNIQUE,
    issued_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at                  TIMESTAMPTZ NOT NULL,
    consumed_at                 TIMESTAMPTZ,
    CONSTRAINT chk_email_verification_token_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX idx_email_verification_token_user_unconsumed
    ON email_verification_token (user_id) WHERE consumed_at IS NULL;

ALTER TABLE mail_outbox
    DROP CONSTRAINT chk_mail_outbox_template;

ALTER TABLE mail_outbox
    ADD CONSTRAINT chk_mail_outbox_template
        CHECK (template IN ('password_reset', 'email_verification'));
