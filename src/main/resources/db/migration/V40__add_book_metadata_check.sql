ALTER TABLE book
    ADD COLUMN metadata_checked_at TIMESTAMPTZ,
    ADD COLUMN verified_fields     TEXT NOT NULL DEFAULT '';
