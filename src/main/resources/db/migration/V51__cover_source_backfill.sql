UPDATE book
SET cover_source = CASE substring(cover_url FROM '^https?://([^/:]+)')
        WHEN 'assets.hardcover.app' THEN 'HARDCOVER'
        WHEN 'books.google.com' THEN 'GOOGLE_BOOKS'
        WHEN 'covers.openlibrary.org' THEN 'OPEN_LIBRARY'
    END
WHERE cover_source IS NULL
  AND cover_url IS NOT NULL;
