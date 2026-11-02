-- Stage 11: manual Whish-to-Whish transfers, NO gateway integration.
-- Keep V1..V10 immutable. Requires MySQL 8.0.16+.
ALTER TABLE companies ADD COLUMN whish_wallet_phone VARCHAR(40) NULL;

ALTER TABLE payments DROP CHECK ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method
  CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','ONLINE','WHISH_TRANSFER'));

CREATE TABLE whish_transfers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  invoice_id BIGINT NULL,
  transfer_reference VARCHAR(120) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency_code CHAR(3) NOT NULL,
  receiver_phone_snapshot VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  reviewed_at DATETIME(6) NULL,
  reviewed_by_user_id BIGINT NULL,
  review_note VARCHAR(500) NULL,
  reconciled_payment_id BIGINT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_whish_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT fk_whish_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  CONSTRAINT fk_whish_order FOREIGN KEY (order_id) REFERENCES customer_orders(id),
  CONSTRAINT fk_whish_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
  CONSTRAINT fk_whish_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_whish_payment FOREIGN KEY (reconciled_payment_id) REFERENCES payments(id),
  CONSTRAINT uq_whish_company_ref UNIQUE (company_id, transfer_reference),
  CONSTRAINT uq_whish_reconciled_payment UNIQUE (reconciled_payment_id),
  CONSTRAINT ck_whish_one_source CHECK ((order_id IS NULL) <> (invoice_id IS NULL)),
  CONSTRAINT ck_whish_amount CHECK (amount > 0),
  CONSTRAINT ck_whish_status CHECK (status IN ('PENDING','VERIFIED','REJECTED')),
  INDEX idx_whish_company_status (company_id, status, submitted_at),
  INDEX idx_whish_order (order_id),
  INDEX idx_whish_invoice (invoice_id)
) ENGINE=InnoDB;
