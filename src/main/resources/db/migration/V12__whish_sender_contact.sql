-- V12: sender-reported details for Whish transfers (distinct from authenticated customer ID).
-- Previous transfers remain valid; nullable columns allow legacy rows.
ALTER TABLE whish_transfers
  ADD COLUMN sender_name VARCHAR(150) NULL,
  ADD COLUMN sender_phone VARCHAR(40) NULL;

UPDATE whish_transfers t
JOIN users u ON u.id = t.customer_id
SET t.sender_name = u.full_name,
    t.sender_phone = u.phone
WHERE t.sender_name IS NULL;



