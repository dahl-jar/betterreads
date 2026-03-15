CREATE TABLE author (
    author_id        BIGSERIAL PRIMARY KEY,
    open_library_key VARCHAR(100) UNIQUE,
    name             VARCHAR(255) NOT NULL,
    bio              TEXT,
    birth_date       VARCHAR(50),
    photo_id         INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE book (
    book_id               BIGSERIAL PRIMARY KEY,
    open_library_work_key VARCHAR(100) UNIQUE,
    title                 VARCHAR(500) NOT NULL,
    subtitle              VARCHAR(500),
    description           TEXT,
    cover_id              INTEGER,
    cover_url             VARCHAR(500),
    first_publish_year    INTEGER,
    isbn                  VARCHAR(20),
    page_count            INTEGER,
    language              VARCHAR(10),
    average_rating        NUMERIC(3,2) DEFAULT 0.0,
    rating_count          INTEGER DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE book_author (
    book_id   BIGINT NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES author(author_id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE book_subject (
    book_subject_id BIGSERIAL PRIMARY KEY,
    book_id         BIGINT NOT NULL REFERENCES book(book_id) ON DELETE CASCADE,
    subject         VARCHAR(255) NOT NULL
);

CREATE INDEX idx_book_work_key ON book(open_library_work_key);
CREATE INDEX idx_book_isbn ON book(isbn);
CREATE INDEX idx_book_title ON book(title);
CREATE INDEX idx_author_ol_key ON author(open_library_key);
CREATE INDEX idx_book_subject_book ON book_subject(book_id);
