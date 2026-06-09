ALTER TABLE book ADD COLUMN cover_object_key TEXT;
ALTER TABLE book ADD COLUMN cover_checked_at TIMESTAMPTZ;

CREATE INDEX idx_book_cover_checked_at ON book (cover_checked_at NULLS FIRST);
