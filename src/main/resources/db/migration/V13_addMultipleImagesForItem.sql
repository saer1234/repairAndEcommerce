-- Clean, optimized database script for V12 / Image setup
CREATE TABLE item_images (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             items_id BIGINT NOT NULL,
                             image_filename VARCHAR(255) NOT NULL,
                             image_type VARCHAR(50) NOT NULL,
                             set_order INT,
                             sort_order INT NOT NULL DEFAULT 1, -- Consolidated directly into the table creation
                             is_primary BOOLEAN DEFAULT TRUE,
                             created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                             PRIMARY KEY (id),
                             FOREIGN KEY (items_id) REFERENCES items(id)
);
