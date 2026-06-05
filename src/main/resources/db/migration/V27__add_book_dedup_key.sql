ALTER TABLE book ADD COLUMN dedup_key TEXT;

UPDATE book
SET dedup_key = COALESCE(
    isbn, open_library_work_key, google_books_volume_id,
    hardcover_id, loc_lccn, wikidata_qid)
WHERE dedup_key IS NULL;

ALTER TABLE book
    ALTER COLUMN dedup_key SET NOT NULL,
    ADD CONSTRAINT book_dedup_key_key UNIQUE (dedup_key);
