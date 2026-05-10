ALTER TABLE app_user
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_app_user_deleted
    ON app_user (deleted_at)
    WHERE deleted_at IS NOT NULL;
