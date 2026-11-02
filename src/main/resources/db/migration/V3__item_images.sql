-- Only the relative server-generated file name is persisted; files live outside the project tree.
ALTER TABLE items
    ADD COLUMN image_filename VARCHAR(80) NULL,
    ADD COLUMN image_content_type VARCHAR(32) NULL;
