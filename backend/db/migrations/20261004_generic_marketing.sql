-- Prerequisite: 20260929_owner_brevo.sql. Stop the old backend before running this.
-- Run the whole script. The transaction preserves the old schema if any step fails.
BEGIN;

DO $$ BEGIN
    IF to_regclass('brevo_connection') IS NOT NULL THEN
        IF to_regclass('marketing_connection') IS NOT NULL THEN
            RAISE EXCEPTION 'Both connection tables exist: resolve the duplicate schema first';
        END IF;
        ALTER TABLE brevo_connection RENAME TO marketing_connection;
    END IF;
    IF to_regclass('brevo_sync_job') IS NOT NULL THEN
        IF to_regclass('marketing_sync_job') IS NOT NULL THEN
            RAISE EXCEPTION 'Both job tables exist: resolve the duplicate schema first';
        END IF;
        ALTER TABLE brevo_sync_job RENAME TO marketing_sync_job;
    END IF;
END $$;

ALTER TABLE marketing_connection ADD COLUMN IF NOT EXISTS provider varchar(40);
ALTER TABLE marketing_connection ADD COLUMN IF NOT EXISTS configuration varchar(4096);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid = 'marketing_connection'::regclass
                 AND attname = 'api_key_ciphertext' AND NOT attisdropped) THEN
        ALTER TABLE marketing_connection RENAME COLUMN api_key_ciphertext TO credentials_ciphertext;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid = 'marketing_connection'::regclass
                 AND attname = 'list_id' AND NOT attisdropped) THEN
        UPDATE marketing_connection SET provider = 'BREVO', configuration = json_build_object(
            'listId', list_id, 'organizationId', organization_id,
            'webhookId', webhook_id, 'webhookSecretHash', webhook_secret_hash)::text;
        ALTER TABLE marketing_connection DROP COLUMN list_id;
        ALTER TABLE marketing_connection DROP COLUMN organization_id;
        ALTER TABLE marketing_connection DROP COLUMN webhook_id;
        ALTER TABLE marketing_connection DROP COLUMN webhook_secret_hash;
    END IF;
END $$;
ALTER TABLE marketing_connection ALTER COLUMN provider SET NOT NULL;
ALTER TABLE marketing_connection ALTER COLUMN configuration SET NOT NULL;

ALTER TABLE marketing_sync_job ADD COLUMN IF NOT EXISTS connection_id bigint;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid = 'marketing_sync_job'::regclass
                 AND attname = 'owner_id' AND NOT attisdropped) THEN
        UPDATE marketing_sync_job j SET connection_id = c.id
            FROM marketing_connection c WHERE j.owner_id = c.owner_id;
        IF EXISTS (SELECT 1 FROM marketing_sync_job WHERE connection_id IS NULL) THEN
            RAISE EXCEPTION 'A pending job has no connection: migration cancelled without deleting data';
        END IF;
        ALTER TABLE marketing_sync_job DROP COLUMN owner_id;
    END IF;
END $$;
ALTER TABLE marketing_sync_job ALTER COLUMN connection_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_marketing_sync_connection'
                   AND conrelid = 'marketing_sync_job'::regclass) THEN
        ALTER TABLE marketing_sync_job ADD CONSTRAINT fk_marketing_sync_connection
            FOREIGN KEY (connection_id) REFERENCES marketing_connection(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_marketing_sync_connection_email'
                   AND conrelid = 'marketing_sync_job'::regclass) THEN
        ALTER TABLE marketing_sync_job ADD CONSTRAINT uk_marketing_sync_connection_email
            UNIQUE (connection_id, email_lookup_hash);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_marketing_sync_updated_at ON marketing_sync_job (updated_at);
DROP INDEX IF EXISTS ix_brevo_sync_updated_at;

-- Table renaming preserves identity sequence ownership and all stored identifiers.
DO $$ BEGIN
    IF to_regclass('brevo_connection_id_seq') IS NOT NULL
       AND to_regclass('marketing_connection_id_seq') IS NULL THEN
        ALTER SEQUENCE brevo_connection_id_seq RENAME TO marketing_connection_id_seq;
    END IF;
    IF to_regclass('brevo_sync_job_id_seq') IS NOT NULL
       AND to_regclass('marketing_sync_job_id_seq') IS NULL THEN
        ALTER SEQUENCE brevo_sync_job_id_seq RENAME TO marketing_sync_job_id_seq;
    END IF;
END $$;

COMMIT;
