SET LOCAL lock_timeout = '5s';

CREATE TABLE book_series (
    book_id     BIGINT  NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    ordinal     INTEGER NOT NULL,
    series_name TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    PRIMARY KEY (book_id, ordinal)
);

INSERT INTO book_series (book_id, ordinal, series_name, position)
SELECT
    book_id,
    0,
    series_name,
    series_position
FROM book
WHERE series_name IS NOT NULL
    AND series_position IS NOT NULL;
