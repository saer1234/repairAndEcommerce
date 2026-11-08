-- Tenant-scoped customer-to-shop support chat. No cascading deletion of conversation history.
CREATE TABLE support_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_support_conversations_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_support_conversations_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT uq_support_company_customer UNIQUE (company_id, customer_id),
    CONSTRAINT ck_support_conversation_status CHECK (status IN ('OPEN', 'CLOSED')),
    INDEX idx_support_company_updated (company_id, updated_at)
) ENGINE=InnoDB;

CREATE TABLE support_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_support_messages_conversation FOREIGN KEY (conversation_id) REFERENCES support_conversations(id),
    CONSTRAINT fk_support_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_support_messages_conversation_id (conversation_id, id)
) ENGINE=InnoDB;
