-- Product publishing is independent from stock availability.
-- Existing active items remain visible, preserving their previous behavior.
ALTER TABLE items
    ADD COLUMN visible_to_clients BOOLEAN NOT NULL DEFAULT TRUE;

-- Previously deactivated products must remain hidden.
UPDATE items SET visible_to_clients = FALSE WHERE active = FALSE;

CREATE INDEX idx_items_client_catalog ON items (company_id, active, visible_to_clients);
