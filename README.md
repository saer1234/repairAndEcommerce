<<<<<<< HEAD
# repairAndEcommerce
full-stack, scalable e-commerce and repair managment platform for phone components featuring real-time inventory tracking, multi-channel ordering, and techincian job worlflows.
=======
# RepairFlow — Stage 7 (Spring Boot API + Angular frontend)

Stage 7 extends the previous Stage 6 MySQL backend with a **JWT Bearer login flow** and an **Angular 22 standalone frontend**. No pre-filled example users or secret keys are included. The backend still uses **Java 21**, **Spring Boot 4.1.1**, **MySQL**, **port 8081**, and the existing Flyway **V1 and V2 migrations**.

## Requirements

- MySQL 8+ running on `localhost:3306`
- Java 21 and Maven 3.9+
- Node.js **22.22.3+** (or 24.15+) for Angular 22, and npm
- A new random **Base64 JWT secret** (at least 32 bytes)

For current Angular/Node requirements, consult https://angular.dev/reference/versions.

## 1. MySQL and environment variables

Create the database if missing:

```sql
CREATE DATABASE IF NOT EXISTS repair_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Generate a new secret locally and **never commit it**:

```bash
openssl rand -base64 32
```

Use this value as `JWT_SECRET_BASE64`. In IntelliJ IDEA, configure Run → Edit Configurations → Environment variables:

```text
DB_USER=root
DB_PASSWORD=<your-local-mysql-password>
JWT_SECRET_BASE64=<your-generated-base64-secret>
CORS_ALLOWED_ORIGINS=http://localhost:4200
```

For production use a dedicated MySQL account, HTTPS, protected secret management, a trusted reverse proxy, and rate limiting at the login endpoint. Rotate keys deliberately: with HMAC signing, rotating the secret invalidates existing tokens.

The project expects the **Flyway V1/V2** migration history from Stage 6. Back up any existing database before upgrading. Stage 7 does not require a new SQL migration.

## 2. Start the API

From the project root:

```bash
mvn test
mvn spring-boot:run
```

Endpoint: `http://localhost:8081/api`.

**Stage 7 changes login behavior:** HTTP Basic Auth from Stage 2–6 is **disabled**. Request a Bearer token via `POST /api/auth/login`; include `Authorization: Bearer <accessToken>` for protected endpoints.

## 3. Start Angular

In another terminal:

```bash
cd frontend
npm install
npm start
```

Visit `http://localhost:4200`.

Angular calls `http://localhost:8081/api` (set in `frontend/src/app/environment.ts`). Change this if your backend URL differs. Only the exact origins in `CORS_ALLOWED_ORIGINS` are allowed.

## 4. First-time workflow

1. Register at `http://localhost:4200/register`.
2. Log in. A short-lived JWT (30 minutes) is stored in **sessionStorage** and restored after a same-tab refresh. It expires at the server-issued time, and sign-out clears it. Closing the tab/window usually clears sessionStorage. There is still no refresh-token or server-side logout revocation.
3. Use **Companies** to create a company. Its creator is assigned OWNER membership.
4. Use **My workspace** for your company management and **Browse customer store** for public shopping at any active company. These selectors are independent. Add registered employees from **Companies**, and manage only workspaces where you are OWNER or MANAGER.
5. Add inventory items using the existing Item API, then use Shop and Repairs in the Angular UI.
6. Managers will see financial and inventory summary cards on their dashboard. Employees see repair and stock counters. Customers see their order and repair statuses.

The company directory (`GET /api/catalog/companies`) exposes **only active company names, IDs, and currency codes** to non-members; user identities and financial data are not included.

## 5. Platform admin access

The public registration endpoint can only create `USER` accounts. Do **not** add a public "select admin" field. For a fresh, trusted local development environment only, promote an account you've already registered:

```sql
UPDATE users SET platform_role='SUPER_ADMIN' WHERE email='your-trusted-email@example.com';
```

Do this directly in a secure admin environment, not through an unauthenticated API, and restrict access to the database. Sign in with that account to view the platform's multi-currency finance overview.

## 6. API quick reference

| Operation | Method | Endpoint | Authentication |
| --- | --- | --- | --- |
| Register | POST | `/api/auth/register` | Public |
| Login | POST | `/api/auth/login` | Public |
| My profile | GET | `/api/me` | Bearer |
| Public company directory | GET | `/api/catalog/companies` | Public |
| Public inventory catalog | GET | `/api/companies/{id}/items` | Public |
| My companies | GET | `/api/companies` | Bearer |
| Create company | POST | `/api/companies` | Bearer |
| Manage team | POST | `/api/companies/{id}/members` | Owner/Manager |
| Company finance | GET | `/api/companies/{id}/finance/overview` | Owner/Manager |
| Company stock | GET | `/api/companies/{id}/inventory/stock` | Company member |
| My orders | GET | `/api/companies/{id}/orders/mine` | Bearer |
| My repairs | GET | `/api/companies/{id}/repairs/mine` | Bearer |
| Platform finance | GET | `/api/platform/finance/overview` | SUPER_ADMIN |

Other Stage 3–6 endpoints still exist; see previous HTTP test files and Stage 6 README. The backend continues to enforce company-level authorization in its services (frontend navigation alone is not a security boundary).

See `http/stage7-auth.http` for request examples and `http/stage7_postman_collection.json` for a Postman import.

## 7. What is and is not implemented

Included: JWT HS256 issuance and signature validation; issuer, audience, and expiry checks; active-user checks; role reloading from MySQL; basic CORS allowlist; Angular login, registration, company/team management, public shop/cart/checkout, repair-request submission, and role-aware overview dashboards. Existing Stage 1–6 backend code and migrations are included.

Not included: automated payments and refunds, password-reset email, email verification, MFA, token refresh/revocation, login throttling / lockouts, full Angular CRUD for every Stage 1–6 endpoint, full Angular test suite, production hosting/HTTPS, database integration tests, or a production-grade accounting system.

**Verification:** TypeScript source syntax and package contents were checked. Full `mvn test`, Angular `npm install`/`npm run build`, and live MySQL API integration tests **could not be performed** in this environment. Treat the deliverable as a development starter until those checks pass on your machine.


## Add items to the store

The Angular frontend includes a **Manage Inventory** page for company OWNER/MANAGER roles, supporting item creation, cost and sale price entry, editing, and restocking. See [ADD_ITEMS_TO_STORE.md](ADD_ITEMS_TO_STORE.md) and [HTTP examples](http/add_items.http).

## Angular session + company navigation update

- Login restores the same-tab session from `sessionStorage` after refresh. Expired/invalid stored tokens are discarded. An API 401 clears the session and returns to login. The short-lived access token cannot be refreshed automatically.
- **Browse customer store** lists all active companies, independent of your membership; customers and managers can view all public catalogs and submit a repair request to a selected company.
- **My workspace (private)** lists only companies in which you are an active member. Company manager inventory, teams, finance and employee repair queue use this selection exclusively; choosing a public store never switches your private workspace.
- The **Companies & teams** page lists public companies for browsing and exposes a separate manager-only team control for the selected private workspace.
- The server continues to enforce company ownership on all protected endpoints. The browser UI does not grant private access to other companies.
- **Security limitation:** `sessionStorage` is accessible to injected JavaScript, so protect against XSS. For production, consider a short-lived access token in memory with HttpOnly Secure SameSite refresh cookie rotation, CSRF defenses as appropriate, and backend token revocation/refresh handling.

Test: log in, refresh, verify you're still logged in; browse another company's store, then open Manage My Store and verify it still points to your own company.

## Item photos

See [ITEM_IMAGES_SETUP.md](ITEM_IMAGES_SETUP.md) for Flyway V3, image storage configuration, the upload API, Angular UI, and security notes.
>>>>>>> fafe4a4 (Initial projecft management frontend, backend and docker setup)

### IT traffic and delivery extensions (V21)

See `WOLFTECH_V21_DASHBOARD_DELIVERY_VALIDATION_TRAFFIC.md`. The platform administrator
can view daily API request/IP aggregates at `/platform-traffic`. For total Docker
container network counters, run `bash scripts/check-network.sh` on the VPS.
