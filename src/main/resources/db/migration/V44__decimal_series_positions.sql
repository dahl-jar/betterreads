SET LOCAL lock_timeout = '5s';

ALTER TABLE book
    ALTER COLUMN series_position TYPE NUMERIC(6, 2);

ALTER TABLE pending_book
    ALTER COLUMN series_position TYPE NUMERIC(6, 2);

ALTER TABLE book_series
    ALTER COLUMN position TYPE NUMERIC(6, 2);
