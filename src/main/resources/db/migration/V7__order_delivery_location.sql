-- Required for NEW orders; nullable for historical order records.
ALTER TABLE customer_orders
    ADD COLUMN customer_name_snapshot VARCHAR(150) NULL,
    ADD COLUMN customer_email_snapshot VARCHAR(190) NULL,
    ADD COLUMN delivery_phone VARCHAR(40) NULL,
    ADD COLUMN delivery_street VARCHAR(255) NULL,
    ADD COLUMN delivery_city VARCHAR(120) NULL,
    ADD COLUMN delivery_postal_code VARCHAR(24) NULL,
    ADD COLUMN delivery_country VARCHAR(100) NULL,
    ADD COLUMN delivery_notes VARCHAR(500) NULL,
    ADD COLUMN delivery_latitude DECIMAL(10,7) NULL,
    ADD COLUMN delivery_longitude DECIMAL(10,7) NULL;
