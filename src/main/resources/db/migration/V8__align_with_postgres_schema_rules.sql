ALTER TABLE app_user
    ALTER COLUMN password_hash TYPE TEXT;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_password_hash_length_check
    CHECK (length(password_hash) = 60) NOT VALID;

ALTER TABLE app_user VALIDATE CONSTRAINT app_user_password_hash_length_check;

ALTER TABLE book
    ALTER COLUMN title TYPE TEXT;

DROP INDEX IF EXISTS idx_book_title;

UPDATE book SET average_rating = NULL WHERE average_rating = 0.0 AND rating_count = 0;

ALTER TABLE book
    ALTER COLUMN average_rating DROP DEFAULT;
