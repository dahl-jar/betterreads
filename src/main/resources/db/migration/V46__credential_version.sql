SET LOCAL lock_timeout = '5s';

ALTER TABLE app_user
    ADD COLUMN credential_version INTEGER NOT NULL DEFAULT 0;
