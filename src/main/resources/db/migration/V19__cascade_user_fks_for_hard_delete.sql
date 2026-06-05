-- Aligns app_user foreign keys so the account-deletion sweep
-- (DELETE FROM app_user WHERE deleted_at < cutoff) works once the V5/V6 features ship.
-- V11 (refresh_token), V16 (email_token), V3 (review), V4 (collection) already cascade.
-- V5 tables and V6 do not.

-- V5: per-user behaviour data — cascade on user delete.
ALTER TABLE user_book_interaction
    DROP CONSTRAINT user_book_interaction_user_id_fkey,
    ADD CONSTRAINT user_book_interaction_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE;

ALTER TABLE user_book_signal
    DROP CONSTRAINT user_book_signal_user_id_fkey,
    ADD CONSTRAINT user_book_signal_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE;

ALTER TABLE user_recommendation
    DROP CONSTRAINT user_recommendation_user_id_fkey,
    ADD CONSTRAINT user_recommendation_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE;

-- V6: activity_event is an audit table. Cascading the actor would lose the audit trail
-- when a user deletes; SET NULL anonymises the actor and keeps the event for downstream
-- reporting. Requires actor_user_id to be nullable.
ALTER TABLE activity_event
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE activity_event
    DROP CONSTRAINT activity_event_actor_user_id_fkey,
    ADD CONSTRAINT activity_event_actor_user_id_fkey
        FOREIGN KEY (actor_user_id) REFERENCES app_user(user_id) ON DELETE SET NULL;
