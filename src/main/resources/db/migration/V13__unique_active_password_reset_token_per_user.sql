DROP INDEX IF EXISTS idx_password_reset_token_user_unconsumed;

CREATE UNIQUE INDEX idx_password_reset_token_user_unconsumed
    ON password_reset_token (user_id) WHERE consumed_at IS NULL;
