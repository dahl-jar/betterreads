ALTER TABLE app_user ALTER COLUMN avatar_url TYPE TEXT;
ALTER TABLE app_user ALTER COLUMN email TYPE TEXT;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_email_length_check
    CHECK (length(email) <= 254) NOT VALID;
ALTER TABLE app_user VALIDATE CONSTRAINT app_user_email_length_check;

ALTER TABLE author ALTER COLUMN open_library_key TYPE TEXT;
ALTER TABLE author ALTER COLUMN name TYPE TEXT;

ALTER TABLE book ALTER COLUMN open_library_work_key TYPE TEXT;
ALTER TABLE book ALTER COLUMN subtitle TYPE TEXT;
ALTER TABLE book ALTER COLUMN cover_url TYPE TEXT;

ALTER TABLE book
    ADD CONSTRAINT book_isbn_format_check
    CHECK (isbn IS NULL OR length(replace(isbn, '-', '')) IN (10, 13)) NOT VALID;
ALTER TABLE book VALIDATE CONSTRAINT book_isbn_format_check;

ALTER TABLE book_subject ALTER COLUMN subject TYPE TEXT;

ALTER TABLE user_recommendation ALTER COLUMN model_version TYPE TEXT;

ALTER TABLE user_book_collection
    ADD CONSTRAINT user_book_collection_status_check
    CHECK (status IN ('want_to_read', 'currently_reading', 'finished', 'dropped')) NOT VALID;
ALTER TABLE user_book_collection VALIDATE CONSTRAINT user_book_collection_status_check;

ALTER TABLE user_book_interaction
    ADD CONSTRAINT user_book_interaction_event_type_check
    CHECK (event_type IN (
        'viewed', 'searched', 'saved', 'want_to_read', 'currently_reading',
        'finished', 'dropped', 'rated', 'reviewed', 'commented',
        'club_joined', 'club_posted'
    )) NOT VALID;
ALTER TABLE user_book_interaction VALIDATE CONSTRAINT user_book_interaction_event_type_check;

ALTER TABLE user_book_interaction
    ADD CONSTRAINT user_book_interaction_event_source_check
    CHECK (event_source IS NULL OR event_source IN (
        'search', 'book_page', 'collection', 'review', 'club', 'feed'
    )) NOT VALID;
ALTER TABLE user_book_interaction VALIDATE CONSTRAINT user_book_interaction_event_source_check;

ALTER TABLE user_recommendation
    ADD CONSTRAINT user_recommendation_reason_check
    CHECK (reason IS NULL OR reason IN (
        'similar_users', 'similar_books', 'same_genre',
        'club_activity', 'trending_in_network', 'hybrid_ranked'
    )) NOT VALID;
ALTER TABLE user_recommendation VALIDATE CONSTRAINT user_recommendation_reason_check;
