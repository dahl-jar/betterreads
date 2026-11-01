SET LOCAL lock_timeout = '5s';

ALTER TABLE pending_book
    ADD COLUMN first_title TEXT;
