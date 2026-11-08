-- V15: ephemeral staff availability and durable customer offline inquiries.
-- Agent sessions expire automatically when heartbeats stop; they are not login tokens.
CREATE TABLE support_agent_presence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_support_presence_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_support_presence_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_support_agent_session UNIQUE (company_id, user_id, session_id),
    INDEX idx_support_presence_company_expiry (company_id, expires_at)
) ENGINE=InnoDB;

CREATE TABLE support_offline_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    reply VARCHAR(2000) NULL,
    replied_by BIGINT NULL,
    replied_at DATETIME(6) NULL,
    CONSTRAINT fk_support_offline_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_support_offline_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_support_offline_staff FOREIGN KEY (replied_by) REFERENCES users(id),
    INDEX idx_support_offline_company_created (company_id, created_at),
    INDEX idx_support_offline_customer_created (company_id, customer_id, created_at)
) ENGINE=InnoDB;
