SET LOCAL lock_timeout = '5s';

ALTER TABLE app_user
    ADD COLUMN signup_notice_sent_at TIMESTAMPTZ;
