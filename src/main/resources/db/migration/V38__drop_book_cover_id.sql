-- cover_id came from the original OpenLibrary-only schema (V2). Covers moved to full URLs in
-- cover_url and MinIO object keys in cover_object_key; nothing ever wrote this column (0 of 6,950
-- production rows carry a value).
ALTER TABLE book
    DROP COLUMN cover_id;
