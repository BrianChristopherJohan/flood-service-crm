-- Email sender registry for the CRM service. Mirrors the same table
-- on flood-service-community but seeds only the two purposes the
-- CRM actually uses (password resets + broadcasts).
--
-- Both Java services share the same Postgres database, so this
-- migration is a no-op when the community service has already
-- created the table — ON CONFLICT skips duplicates.

CREATE TABLE IF NOT EXISTS email_senders (
    id            UUID PRIMARY KEY,
    purpose       VARCHAR(32)  NOT NULL UNIQUE,
    from_address  VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO email_senders (id, purpose, from_address, display_name)
VALUES
    (gen_random_uuid(), 'PASSWORD_RESET', 'noreply@floodwatch.app', 'FloodWatch'),
    (gen_random_uuid(), 'BROADCAST',      'alerts@floodwatch.app',  'FloodWatch Alerts')
ON CONFLICT (purpose) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_email_senders_active
    ON email_senders (active) WHERE active = TRUE;
