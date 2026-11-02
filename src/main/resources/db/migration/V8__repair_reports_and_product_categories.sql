-- Version 8: catalog classification and reproducible customer repair explanations.
ALTER TABLE items ADD COLUMN category VARCHAR(80) NOT NULL DEFAULT 'General';
ALTER TABLE repair_requests ADD COLUMN diagnosis TEXT NULL, ADD COLUMN work_performed TEXT NULL;
ALTER TABLE invoices
  ADD COLUMN repair_technician_snapshot VARCHAR(150) NULL,
  ADD COLUMN repair_diagnosis_snapshot TEXT NULL,
  ADD COLUMN repair_work_snapshot TEXT NULL,
  ADD COLUMN repair_device_snapshot VARCHAR(360) NULL,
  ADD COLUMN repair_labor_cost_snapshot DECIMAL(12,2) NULL,
  ADD COLUMN repair_parts_cost_snapshot DECIMAL(12,2) NULL;
CREATE INDEX idx_items_company_category ON items(company_id, category);
