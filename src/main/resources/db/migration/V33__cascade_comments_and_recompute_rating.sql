CREATE FUNCTION delete_review_comments() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM comment WHERE target_type = 'REVIEW' AND target_id = OLD.review_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER review_comments_cascade
    BEFORE DELETE ON review
    FOR EACH ROW
    EXECUTE FUNCTION delete_review_comments();

CREATE FUNCTION recompute_book_rating() RETURNS TRIGGER AS $$
DECLARE
    locked_book_id BIGINT;
    new_average NUMERIC(3, 2);
    new_count INTEGER;
BEGIN
    SELECT book_id INTO locked_book_id FROM book
    WHERE book_id = OLD.book_id AND community_rated = TRUE FOR UPDATE;
    IF locked_book_id IS NULL THEN
        RETURN OLD;
    END IF;
    SELECT ROUND(AVG(rating), 2), COUNT(rating) INTO new_average, new_count
    FROM review WHERE book_id = OLD.book_id AND rating IS NOT NULL;
    UPDATE book SET average_rating = new_average, rating_count = new_count
    WHERE book_id = locked_book_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER review_rating_recompute
    AFTER DELETE ON review
    FOR EACH ROW
    EXECUTE FUNCTION recompute_book_rating();

CREATE FUNCTION delete_book_comments() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM comment WHERE target_type = 'BOOK' AND target_id = OLD.book_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER book_comments_cascade
    BEFORE DELETE ON book
    FOR EACH ROW
    EXECUTE FUNCTION delete_book_comments();
