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

CREATE TABLE item_images(ID BIGINT NOT NULL AUTO_INCREMENT,items_id BIGINT NOT NULL,image_filename VARCHAR(255) NOT NULL, image_type VARCHAR(50) NOT NULL, set_order INT , is_primary BOOLEAN DEFAULT TRUE,created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(ID), FOREIGN KEY (items_id) REFERENCES items(id));
ALTER TABLE item_images ADD COLUMN sort_order INT NOT NULL DEFAULT 1;


