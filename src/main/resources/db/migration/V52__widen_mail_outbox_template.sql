SET LOCAL lock_timeout = '5s';

ALTER TABLE mail_outbox
    DROP CONSTRAINT chk_mail_outbox_template;

ALTER TABLE mail_outbox
    ADD CONSTRAINT chk_mail_outbox_template
        CHECK (template IN ('password_reset', 'email_verification', 'signup_attempt'));
