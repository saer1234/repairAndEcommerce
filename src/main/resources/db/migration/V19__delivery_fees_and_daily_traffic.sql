-- Fulfillment snapshots and configurable company delivery fees.
ALTER TABLE companies
  ADD COLUMN delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE customer_orders
  ADD COLUMN fulfillment_method VARCHAR(20) NOT NULL DEFAULT 'DELIVERY',
  ADD COLUMN delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  ADD COLUMN items_subtotal DECIMAL(12,2) NULL;
UPDATE customer_orders SET items_subtotal=total_amount WHERE items_subtotal IS NULL;
CREATE INDEX idx_orders_company_completed_at ON customer_orders(company_id, completed_at);
CREATE INDEX idx_repairs_company_completed_at ON repair_requests(company_id, completed_at);

-- Raw IPs are restricted to platform admins and automatically deleted after 7 days.
CREATE TABLE daily_http_traffic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_day DATE NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    inbound_bytes BIGINT NOT NULL DEFAULT 0,
    outbound_bytes_known BIGINT NOT NULL DEFAULT 0,
    last_seen_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_traffic_day_ip (request_day, ip_address),
    INDEX idx_traffic_day (request_day)
);
