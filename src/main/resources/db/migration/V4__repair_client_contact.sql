-- A repair records the client's contact details at request time, independently of
-- the associated login account. Existing historical repairs remain valid.
ALTER TABLE repair_requests
    ADD COLUMN client_name VARCHAR(150) NULL,
    ADD COLUMN client_phone VARCHAR(40) NULL;

-- Best-effort backfill of historical repairs from the account used to submit them.
UPDATE repair_requests r
INNER JOIN users u ON u.id = r.customer_id
SET r.client_name = u.full_name,
    r.client_phone = NULLIF(TRIM(u.phone), '');
