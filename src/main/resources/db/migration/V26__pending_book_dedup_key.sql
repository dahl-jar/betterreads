ALTER TABLE pending_book ADD COLUMN dedup_key TEXT;

UPDATE pending_book
SET dedup_key = COALESCE(
    isbn13, open_library_work_key, google_books_volume_id,
    hardcover_id, loc_lccn, wikidata_qid)
WHERE dedup_key IS NULL;

ALTER TABLE pending_book
    ALTER COLUMN dedup_key SET NOT NULL,
    ADD CONSTRAINT pending_book_dedup_key_key UNIQUE (dedup_key);
