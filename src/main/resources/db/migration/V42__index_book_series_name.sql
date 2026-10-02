SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_book_series_name ON book_series(series_name, position);
