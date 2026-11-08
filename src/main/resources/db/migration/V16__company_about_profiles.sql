-- Each company controls its own public About Us details. Existing companies retain name/contact fallbacks.
CREATE TABLE company_about_profiles (
  company_id BIGINT NOT NULL PRIMARY KEY,
  headline VARCHAR(180) NULL,
  description VARCHAR(3000) NULL,
  store_address VARCHAR(255) NULL,
  contact_phone VARCHAR(40) NULL,
  contact_email VARCHAR(190) NULL,
  maps_url VARCHAR(500) NULL,
  instagram_url VARCHAR(500) NULL,
  facebook_url VARCHAR(500) NULL,
  tiktok_url VARCHAR(500) NULL,
  whatsapp_url VARCHAR(500) NULL,
  website_url VARCHAR(500) NULL,
  opening_hours VARCHAR(500) NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_company_about_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
