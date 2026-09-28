-- Match the limits enforced by EventRequest and the event editor.
-- Apply to existing PostgreSQL databases before starting with schema validation.
BEGIN;
ALTER TABLE public.event
    ALTER COLUMN description TYPE varchar(4000),
    ALTER COLUMN image_url TYPE varchar(2048);
COMMIT;
