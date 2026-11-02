-- Only explicitly written customer-facing notes are exposed in tracking.
-- Existing internal note entries are not copied into the public field.
ALTER TABLE repair_updates
    ADD COLUMN customer_note VARCHAR(2000) NULL;
