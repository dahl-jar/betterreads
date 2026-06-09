ALTER TABLE book ADD COLUMN description_checked_at TIMESTAMPTZ;

CREATE INDEX idx_book_description_checked_at ON book (description_checked_at NULLS FIRST);
