ALTER TABLE app_user DROP CONSTRAINT app_user_username_key;
DROP INDEX idx_app_user_username;
CREATE UNIQUE INDEX idx_app_user_username_lower ON app_user (lower(username));
