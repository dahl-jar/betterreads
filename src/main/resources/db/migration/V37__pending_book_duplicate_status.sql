ALTER TABLE pending_book DROP CONSTRAINT pending_book_status_check;
ALTER TABLE pending_book ADD CONSTRAINT pending_book_status_check
    CHECK (status IN ('PENDING', 'PROMOTED', 'INCOMPLETE_FINAL', 'DUPLICATE'));
