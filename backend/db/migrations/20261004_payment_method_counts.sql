BEGIN;

ALTER TABLE booking ADD COLUMN IF NOT EXISTS payment_method varchar(255);
ALTER TABLE event ADD COLUMN IF NOT EXISTS walk_in_cash_count integer NOT NULL DEFAULT 0;
ALTER TABLE event ADD COLUMN IF NOT EXISTS walk_in_card_count integer NOT NULL DEFAULT 0;

-- Historical entries stay unclassified: never infer a payment method.
ALTER TABLE booking ADD CONSTRAINT ck_booking_payment_method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD'));
ALTER TABLE event ADD CONSTRAINT ck_event_payment_counts
    CHECK (walk_in_cash_count >= 0 AND walk_in_card_count >= 0
        AND walk_in_cash_count + walk_in_card_count <= walk_in_count);

COMMIT;
