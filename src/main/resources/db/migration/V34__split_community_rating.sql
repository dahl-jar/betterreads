ALTER TABLE book ADD COLUMN community_average NUMERIC(3, 2);
ALTER TABLE book ADD COLUMN community_count INTEGER NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION recompute_book_rating() RETURNS TRIGGER AS $$
DECLARE
    locked_book_id BIGINT;
    new_average NUMERIC(3, 2);
    new_count INTEGER;
BEGIN
    SELECT book_id INTO locked_book_id FROM book
    WHERE book_id = OLD.book_id FOR UPDATE;
    IF locked_book_id IS NULL THEN
        RETURN OLD;
    END IF;
    SELECT ROUND(AVG(rating), 2), COUNT(rating) INTO new_average, new_count
    FROM review WHERE book_id = OLD.book_id AND rating IS NOT NULL;
    UPDATE book SET community_average = new_average, community_count = new_count
    WHERE book_id = locked_book_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE book DROP COLUMN community_rated;
