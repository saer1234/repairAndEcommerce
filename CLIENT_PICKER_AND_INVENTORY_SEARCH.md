# Client account picker and inventory search

This upgrade is based on the owner/client rules project. No MySQL migration is required.

## Behavior

- Customer-facing **My Repairs**: a signed-in `CLIENT` without active company memberships can submit a repair; its `client_name` comes from the authenticated user account and is not editable.
- **Repair Workspace**: an authenticated active company OWNER, MANAGER, or EMPLOYEE can search for a registered customer by name/email (at least 2 characters), select the customer account (name + email), and create a repair on their behalf. Only active `CLIENT` accounts **with zero active company memberships anywhere** qualify. Owner, Manager, Employee, Super Admin and legacy USER accounts do not appear. Account name is never sent in the staff form; the server uses the selected account's name and links the repair to its user ID. Phone is editable for this repair.
- Backend verifies roles/membership on both lookup and creation; clients are revalidated on save. The client can then track that repair on their account.
- **Store inventory**: instant client-side filtering of the selected company's already-loaded inventory by product name, SKU, or description; no database changes.

## New backend API

`GET /api/companies/{companyId}/repairs/eligible-clients?q=ali` (staff only; 2–80 character query, max 20 names)

Response example:
```json
[{"id":15,"fullName":"Alice Example","email":"alice@example.com","phone":"+96170123456"}]
```

`POST /api/companies/{companyId}/repairs/for-client` (staff only):
```json
{
  "clientId": 15,
  "clientPhone": "+96170123456",
  "deviceType": "Phone",
  "brand": "Samsung",
  "model": "S24",
  "issueDescription": "Broken screen"
}
```

## Installing

1. Back up the current project and MySQL database.
2. Extract the **patch** ZIP at the **project root** so the `repair_management_stage7/` directory is merged with your existing project, replacing the matching files. Or open the complete ZIP as a separate project.
3. Restart the Spring Boot API (`mvn test`, then `mvn spring-boot:run`) on `http://localhost:8081`.
4. Run Angular (`cd frontend`, `npm install` if necessary, `npm start`) on `http://localhost:4200`.

## Test checklist

1. Log in as company staff. Visit Repair Workspace and search for an eligible client by name/email. Confirm no active staff accounts appear.
2. Select client; submit a repair. Inspect the returned repair: `customerId` must be the selected user's ID and `clientName` must match that account's name. Client should see the repair on **Track My Repairs**.
3. Attempt `POST .../repairs/for-client` with an owner or employee's ID, bypassing Angular. Server must return HTTP 403.
4. Log in as a standard client without company membership, create repair, confirm name is taken from login session. Try to forge `clientName` in the self-serve request; name must still match account.
5. Open Store Inventory, type a product name, SKU or description. Verify results narrow immediately and clearing the query restores the full list.

Security note: customer lookup exposes only limited contact data to authenticated company staff. Before deploying publicly, add search rate-limiting and appropriate data retention/access policies. Do not treat the JavaScript dropdown as a security boundary.

## Verification scope

This package is source-code prepared. Full Maven tests, Angular compilation and live MySQL integration have not been executed in this environment.
