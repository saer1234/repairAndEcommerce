# Registration, company creation and repair name restrictions

## Behavior

- Public sign-up shows **Client** and **Owner** only. No company Manager, Employee, or Super Admin sign-up option.
- An Owner can create a company. A Client cannot create one, even if they manually call `POST /api/companies`.
- Managers and Employees remain **company membership roles assigned by existing owners/managers**; they are not registration types.
- A repair request always stores a snapshot of the **authenticated account full name** in `repair_requests.client_name`. The client name is read-only in Angular. The phone remains editable. The backend ignores any `clientName` sent by older clients.
- Customers retain access to public company browsing, shopping, repair requests, and tracking.

## Install

1. Back up your MySQL database and existing project.
2. Extract the patch into the existing project root, preserving paths; or open the full project instead.
3. Restart Spring Boot, letting Flyway apply `V6__public_account_types.sql`.
4. Start Angular, visit `/register`, and verify the two account types.
5. Test registration as a Client and confirm `POST /api/companies` returns HTTP 403, even when called directly.
6. Register an Owner, sign in, and confirm company creation works.
7. As a Client, submit a repair. Verify `repair_requests.client_name` equals the account `full_name` even if you send a different `clientName` in the API body.

## Migration

`V6__public_account_types.sql` expands the `users.platform_role` CHECK constraint and converts existing account types:
- existing generic users with active OWNER memberships become Owner accounts;
- remaining generic USER accounts become Client accounts;
- SUPER_ADMIN is unchanged, and can never be self-registered.

Flyway migration V6 assumes the project's preceding V1–V5 scripts are in place, and MySQL 8.0.16+ with working CHECK constraints. No schema change to `company_members` or `repair_requests` is required.

## Local verification

```
mvn test
mvn spring-boot:run
cd frontend
npm install
npm run build
```

**Security note**: The Owner account type is self-selectable. This blocks ordinary Client accounts from creating companies, but it is not business-owner identity verification. If you want shop-owner approval, add an admin approval or verification workflow before granting account creation rights.
