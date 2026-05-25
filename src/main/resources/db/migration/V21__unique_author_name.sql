DELETE FROM book_author ba
USING author a
WHERE ba.author_id = a.author_id
    AND EXISTS (
        SELECT 1 FROM author a2
        WHERE a2.name = a.name AND a2.author_id < a.author_id
    );

WITH ranked AS (
    SELECT author_id, name,
        ROW_NUMBER() OVER (PARTITION BY name ORDER BY author_id) AS rn
    FROM author
)
DELETE FROM author
WHERE author_id IN (SELECT author_id FROM ranked WHERE rn > 1);

ALTER TABLE author
    ADD CONSTRAINT author_name_key UNIQUE (name);
