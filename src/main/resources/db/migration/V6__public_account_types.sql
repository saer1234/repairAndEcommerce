-- Public sign-up supports Owner and Client only. Team membership roles are unchanged.
-- Preserve USER in the constraint for older accounts and SUPER_ADMIN for provisioning.
ALTER TABLE users DROP CHECK ck_users_platform_role;
ALTER TABLE users ADD CONSTRAINT ck_users_platform_role
    CHECK (platform_role IN ('USER', 'OWNER', 'CLIENT', 'SUPER_ADMIN'));

-- Preserve ability to establish additional companies for existing company owners.
UPDATE users u
   SET u.platform_role = 'OWNER'
 WHERE u.platform_role = 'USER'
   AND EXISTS (
       SELECT 1 FROM company_members cm
       WHERE cm.user_id = u.id AND cm.member_role = 'OWNER' AND cm.active = TRUE
   );

-- Convert remaining legacy generic accounts into customer/client accounts.
UPDATE users SET platform_role = 'CLIENT' WHERE platform_role = 'USER';
