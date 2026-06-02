ALTER TABLE book
    ADD COLUMN wikidata_qid TEXT,
    ADD CONSTRAINT book_wikidata_qid_key UNIQUE (wikidata_qid);

CREATE TABLE book_award (
    book_award_id BIGSERIAL PRIMARY KEY,
    book_id       BIGINT NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    award         TEXT NOT NULL
);

CREATE INDEX idx_book_award_book ON book_award(book_id);

ALTER TABLE author
    ADD COLUMN wikidata_qid TEXT,
    ADD COLUMN photo_url    TEXT,
    ADD CONSTRAINT author_wikidata_qid_key UNIQUE (wikidata_qid);
