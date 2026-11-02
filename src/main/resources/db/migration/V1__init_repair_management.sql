-- Repair Management Platform: initial schema (MySQL 8.0.16+ / 8.4)
-- Apply to a NEW, EMPTY repair_management database using Flyway.
-- All monetary values are DECIMAL; all application timestamps should be stored in UTC.
-- Company currency_code determines the currency of a company's prices and transactions.
-- Financial history is intentionally protected from accidental cascading deletion.

-- 1) Identity and companies
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(40) NULL,
    platform_role VARCHAR(30) NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_platform_role CHECK (platform_role IN ('USER', 'SUPER_ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(180) NOT NULL,
    email VARCHAR(190) NULL,
    phone VARCHAR(40) NULL,
    address VARCHAR(255) NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_companies_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
) ENGINE=InnoDB;

-- Users can be customers and members of one or more companies.
-- Role is specific to the company, not to the user's identity globally.
CREATE TABLE company_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(25) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_company_members_company_user UNIQUE (company_id, user_id),
    CONSTRAINT fk_company_members_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_company_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_company_members_role CHECK (member_role IN ('OWNER', 'MANAGER', 'EMPLOYEE'))
) ENGINE=InnoDB;

-- 2) Inventory
CREATE TABLE items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    sku VARCHAR(90) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT NULL,
    cost_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    selling_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    stock_quantity INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_items_company_sku UNIQUE (company_id, sku),
    CONSTRAINT fk_items_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT ck_items_prices CHECK (cost_price >= 0 AND selling_price >= 0),
    CONSTRAINT ck_items_stock CHECK (stock_quantity >= 0),
    INDEX idx_items_company_name (company_id, name)
) ENGINE=InnoDB;

CREATE TABLE stock_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    performed_by_user_id BIGINT NULL,
    movement_type VARCHAR(25) NOT NULL,
    quantity_change INT NOT NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (performed_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity_change <> 0),
    CONSTRAINT ck_stock_movements_type CHECK (movement_type IN ('RESTOCK', 'SALE', 'REPAIR', 'RETURN', 'ADJUSTMENT')),
    INDEX idx_stock_movements_item_date (item_id, created_at)
) ENGINE=InnoDB;

-- 3) Carts and orders
CREATE TABLE shopping_carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_shopping_carts_company_customer UNIQUE (company_id, customer_id),
    CONSTRAINT fk_shopping_carts_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_shopping_carts_customer FOREIGN KEY (customer_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_cart_items_cart_item UNIQUE (cart_id, item_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES shopping_carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE customer_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(60) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ordered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_customer_orders_company_number UNIQUE (company_id, order_number),
    CONSTRAINT fk_customer_orders_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_customer_orders_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_customer_orders_total CHECK (total_amount >= 0),
    INDEX idx_customer_orders_company_date (company_id, ordered_at),
    INDEX idx_customer_orders_customer_date (customer_id, ordered_at)
) ENGINE=InnoDB;

-- Selling and cost price SNAPSHOTS protect historical profit from price edits.
CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name_snapshot VARCHAR(180) NOT NULL,
    quantity INT NOT NULL,
    unit_selling_price DECIMAL(12, 2) NOT NULL,
    unit_cost_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_order_items_order_item UNIQUE (order_id, item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES customer_orders(id),
    CONSTRAINT fk_order_items_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_prices CHECK (unit_selling_price >= 0 AND unit_cost_price >= 0)
) ENGINE=InnoDB;

-- 4) Repairs
CREATE TABLE repair_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    assigned_employee_id BIGINT NULL,
    device_type VARCHAR(100) NOT NULL,
    brand VARCHAR(100) NULL,
    model VARCHAR(120) NULL,
    issue_description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    labor_charge DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    labor_cost DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_repair_requests_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_repair_requests_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_repair_requests_employee FOREIGN KEY (assigned_employee_id) REFERENCES users(id),
    CONSTRAINT ck_repair_requests_status CHECK (status IN ('NEW', 'DIAGNOSING', 'WAITING_PARTS', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_repair_requests_costs CHECK (labor_charge >= 0 AND labor_cost >= 0),
    INDEX idx_repair_requests_company_status (company_id, status),
    INDEX idx_repair_requests_customer_date (customer_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE repair_parts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repair_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_selling_price DECIMAL(12, 2) NOT NULL,
    unit_cost_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_repair_parts_repair FOREIGN KEY (repair_id) REFERENCES repair_requests(id),
    CONSTRAINT fk_repair_parts_item FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT ck_repair_parts_quantity CHECK (quantity > 0),
    CONSTRAINT ck_repair_parts_prices CHECK (unit_selling_price >= 0 AND unit_cost_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE repair_updates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repair_id BIGINT NOT NULL,
    updated_by_user_id BIGINT NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_repair_updates_repair FOREIGN KEY (repair_id) REFERENCES repair_requests(id),
    CONSTRAINT fk_repair_updates_user FOREIGN KEY (updated_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_repair_updates_status CHECK (new_status IN ('NEW', 'DIAGNOSING', 'WAITING_PARTS', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED')),
    INDEX idx_repair_updates_repair_date (repair_id, created_at)
) ENGINE=InnoDB;

-- 5) Invoices and payments. An invoice may come from an order, a repair,
-- or be entered manually. Link at most one source in application logic.
CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    repair_id BIGINT NULL,
    invoice_number VARCHAR(60) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    subtotal DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    issued_at DATETIME(6) NULL,
    due_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_invoices_company_number UNIQUE (company_id, invoice_number),
    CONSTRAINT fk_invoices_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES customer_orders(id),
    CONSTRAINT fk_invoices_repair FOREIGN KEY (repair_id) REFERENCES repair_requests(id),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID')),
    CONSTRAINT ck_invoices_amounts CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_invoices_company_date (company_id, issued_at)
) ENGINE=InnoDB;

CREATE TABLE invoice_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL DEFAULT 1.00,
    unit_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT ck_invoice_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_lines_price CHECK (unit_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    provider_reference VARCHAR(190) NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'ONLINE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    INDEX idx_payments_invoice_status (invoice_id, status)
) ENGINE=InnoDB;

-- 6) Financial tracking
-- company_id NULL means a platform-level expense; NOT NULL means a company expense.
CREATE TABLE expenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(255) NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    expense_date DATE NOT NULL,
    created_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_expenses_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_expenses_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_expenses_amount CHECK (amount > 0),
    INDEX idx_expenses_company_date (company_id, expense_date)
) ENGINE=InnoDB;

-- Fees (commission / subscription) earned by the platform from companies.
-- PENDING/REFUNDED fees should not be included in earned revenue.
CREATE TABLE platform_fees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    repair_id BIGINT NULL,
    fee_type VARCHAR(25) NOT NULL,
    fee_amount DECIMAL(12, 2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    earned_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_platform_fees_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_platform_fees_order FOREIGN KEY (order_id) REFERENCES customer_orders(id),
    CONSTRAINT fk_platform_fees_repair FOREIGN KEY (repair_id) REFERENCES repair_requests(id),
    CONSTRAINT ck_platform_fees_type CHECK (fee_type IN ('COMMISSION', 'SUBSCRIPTION', 'OTHER')),
    CONSTRAINT ck_platform_fees_status CHECK (status IN ('PENDING', 'EARNED', 'REFUNDED')),
    CONSTRAINT ck_platform_fees_amount CHECK (fee_amount >= 0),
    INDEX idx_platform_fees_company_date (company_id, earned_at)
) ENGINE=InnoDB;
