-- Each item may have one current percentage discount. Original list price remains on items.
-- Discounted checkout prices are snapshotted in order_items.unit_selling_price.
CREATE TABLE item_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    discount_percent INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_item_offers_item UNIQUE (item_id),
    CONSTRAINT fk_item_offers_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT chk_item_offers_percent CHECK (discount_percent BETWEEN 1 AND 90)
);
