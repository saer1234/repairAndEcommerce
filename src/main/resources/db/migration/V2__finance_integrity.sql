-- Stage 6: protect invoice sources and manually received payment references.
-- Existing invoices must be checked for duplicates before applying this migration.
ALTER TABLE invoices
  ADD CONSTRAINT ck_invoices_source_exclusive
     CHECK (order_id IS NULL OR repair_id IS NULL);
CREATE UNIQUE INDEX uq_invoices_order_id ON invoices(order_id);
CREATE UNIQUE INDEX uq_invoices_repair_id ON invoices(repair_id);
CREATE UNIQUE INDEX uq_payments_invoice_reference ON payments(invoice_id, provider_reference);
-- One source may be charged one commission of a given type. NULLs remain unrestricted.
CREATE UNIQUE INDEX uq_platform_fees_order_type ON platform_fees(order_id, fee_type);
CREATE UNIQUE INDEX uq_platform_fees_repair_type ON platform_fees(repair_id, fee_type);
