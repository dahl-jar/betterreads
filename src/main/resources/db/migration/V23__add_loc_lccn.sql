ALTER TABLE book
    ADD COLUMN loc_lccn TEXT,
    ADD CONSTRAINT book_loc_lccn_key UNIQUE (loc_lccn);
