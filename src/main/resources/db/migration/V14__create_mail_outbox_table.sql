CREATE TABLE mail_outbox (
    mail_outbox_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template        TEXT NOT NULL,
    recipient       TEXT NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    attempt_count   INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    CONSTRAINT chk_mail_outbox_template CHECK (template IN ('password_reset')),
    CONSTRAINT chk_mail_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_mail_outbox_terminal_states
        CHECK (NOT (sent_at IS NOT NULL AND failed_at IS NOT NULL))
);

CREATE INDEX idx_mail_outbox_pending
    ON mail_outbox (next_attempt_at)
    WHERE sent_at IS NULL AND failed_at IS NULL;
