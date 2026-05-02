DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'betterreads_app') THEN
        EXECUTE 'CREATE ROLE betterreads_app LOGIN PASSWORD ' || quote_literal('${appPassword}');
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO betterreads_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO betterreads_app;

GRANT USAGE, SELECT, UPDATE
    ON ALL SEQUENCES IN SCHEMA public
    TO betterreads_app;

REVOKE ALL ON flyway_schema_history FROM betterreads_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO betterreads_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO betterreads_app;
