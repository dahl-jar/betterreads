SET LOCAL lock_timeout = '5s';

ALTER TABLE refresh_token
    ADD COLUMN persistent BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE refresh_token
    ALTER COLUMN persistent DROP DEFAULT;

WITH RECURSIVE chain AS (
    SELECT refresh_token_id AS active_id, refresh_token_id AS current_id, issued_at
    FROM refresh_token
    WHERE revoked_at IS NULL
    UNION ALL
    SELECT chain.active_id, previous.refresh_token_id, previous.issued_at
    FROM chain
    JOIN refresh_token previous ON previous.replaced_by = chain.current_id
),
login AS (
    SELECT active_id, min(issued_at) + interval '30 days' AS ends_at
    FROM chain
    GROUP BY active_id
)
UPDATE refresh_token token
SET revoked_at = CASE WHEN login.ends_at <= now() THEN now() END,
    expires_at = CASE WHEN login.ends_at <= now() THEN token.expires_at ELSE login.ends_at END
FROM login
WHERE token.refresh_token_id = login.active_id;

UPDATE refresh_token token
SET revoked_at = now()
FROM app_user reader
WHERE reader.user_id = token.user_id
    AND reader.email_verified_at IS NULL
    AND token.revoked_at IS NULL;
