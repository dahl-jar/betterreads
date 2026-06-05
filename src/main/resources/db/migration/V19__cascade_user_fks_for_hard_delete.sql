-- Aligns app_user foreign keys so the account-deletion sweep
-- (DELETE FROM app_user WHERE deleted_at < cutoff) works once the V5/V6 features ship.
-- The V11, V16, V3, and V4 tables already cascade; the V5 and V6 tables are fixed here.

-- V5: per-user behaviour data, cascades on user delete.
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
-- when a user deletes; SET NULL anonymises the actor and keeps the event row.
-- Requires actor_user_id to be nullable.
ALTER TABLE activity_event
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE activity_event
    DROP CONSTRAINT activity_event_actor_user_id_fkey,
    ADD CONSTRAINT activity_event_actor_user_id_fkey
        FOREIGN KEY (actor_user_id) REFERENCES app_user(user_id) ON DELETE SET NULL;
