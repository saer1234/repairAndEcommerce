-- Device type -> brand classification and cross-device product fitment.
-- V1-V8 must already be applied before this migration.
ALTER TABLE items
  ADD COLUMN device_type VARCHAR(100) NOT NULL DEFAULT 'General',
  ADD COLUMN device_brand VARCHAR(100) NOT NULL DEFAULT 'Universal';

-- Preserve existing V8 categories as the best available device type.
UPDATE items SET device_type = COALESCE(NULLIF(TRIM(category), ''), 'General');

CREATE INDEX idx_items_company_device ON items(company_id, device_type, device_brand);

CREATE TABLE item_compatibilities (
    item_id BIGINT NOT NULL,
    device_type VARCHAR(100) NOT NULL,
    device_brand VARCHAR(100) NOT NULL,
    device_model VARCHAR(120) NOT NULL,
    CONSTRAINT fk_compat_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT uq_item_compatibility UNIQUE (item_id, device_type, device_brand, device_model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_compat_model ON item_compatibilities(device_type, device_brand, device_model);
