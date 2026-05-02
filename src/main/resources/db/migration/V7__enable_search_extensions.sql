CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE INDEX IF NOT EXISTS idx_book_title_trgm ON book USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_author_name_trgm ON author USING gin (name gin_trgm_ops);
