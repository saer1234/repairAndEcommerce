# WolfTech V20 — Transparent login/registration logo + Platform Owner User Directory

## Changes
- On Login and Register pages, switched from `assets/wolflog.jfif` (opaque white background) to the already-existing transparent `assets/wolflog-removebg-preview.png`.
- Added a responsive read-only **All Users** page (`/platform-users`) with search by full name/email, pagination, account type, active flag and signup date.
- Added the protected `GET /api/platform/users?page=0&size=20&search=` API. Server-side query and pagination, maximum 100 records/page, no password hashes.
- The endpoint is protected by Spring Security's existing `/api/platform/**` SUPER_ADMIN-only rule **and** a service-level role check.
- No database schema changes and no Flyway migration required.

## Crucial role distinction
An ordinary `OWNER` account can be created via public registration and is a **company/shop owner**, not the owner of all users on the platform. Giving every `OWNER` account a worldwide customer listing would disclose other shops' customers.

Only the privately provisioned `SUPER_ADMIN` platform owner can load the complete website user directory. If your authorized platform-owner account is not yet SUPER_ADMIN, provision it in a controlled administrative procedure (after verifying that the selected user really controls the site). From a trusted MySQL admin session with a database backup: `UPDATE users SET platform_role='SUPER_ADMIN' WHERE email='your-verified-platform-admin@example.com';` Then sign out and sign back in. **Do not add SUPER_ADMIN as a public sign-up option.** If your site is operated by multiple independent companies, you may want a different shop-specific customer listing for each OWNER later.

## Install
1. Make a git commit / backup of your local project before applying the patch.
2. Extract the patch into the repository root so `frontend/` and `src/` merge with your current files. Review conflicts, especially if local code changed since V19.
3. `mvn clean test` at repository root; `cd frontend && npm ci && npm run build`.
4. Test normal CLIENT, OWNER, MANAGER and EMPLOYEE accounts cannot access `/api/platform/users` (403). Test SUPER_ADMIN sees users and can search/page.
5. Push changes to GitHub and deploy only frontend+backend containers (`docker compose up -d --no-deps --build backend frontend`).

This patch is based on the V19 source package available in this conversation, not an independently fetched current GitHub branch.
