# Customer repair tracking — upgrade

## Included changes

- A dedicated Angular **Track My Repairs** page at `/track-repairs`.
- Cross-company customer repair listing, an order-independent detail view, a 5-step progress indicator, and a timeline of status changes and approved public notes.
- Automatically checks for changes every 30 seconds while the tracking page is open, with a manual Refresh button. No push notifications or SMS in this version.
- **Staff-only** private note and a **customer-facing** message separated in the Repair Workspace.
- Staff can publish a progress note without changing repair status, and the private workspace history displays both internal and published notes.
- Customer requests and repair details no longer expose employee IDs or private update notes. Internal costs remain private to authorized company managers.
- Secure tracking API requires JWT login; users can fetch only repairs submitted from their own accounts. It is not a public link or a phone-number lookup.

## Install

1. Back up project and MySQL database.
2. Extract patch ZIP into the root of your existing application, replacing matching files. The complete ZIP already includes Stages 1–7 and the earlier image and client-contact upgrades.
3. Restart Spring Boot. Flyway will run `V5__repair_customer_tracking.sql` and add nullable column `repair_updates.customer_note`.
4. Verify MySQL schema migration state: `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`.
5. Build backend and frontend locally:

   ```bash
   mvn test
   mvn spring-boot:run
   # second terminal:
   cd frontend
   npm install
   npm run build
   npm start
   ```

6. Login as a customer. Click **Track My Repairs**; you will see all repairs from your account, across companies, independent of the public store selector.
7. Login as an owner or an employee assigned to a repair. Open **Repair Workspace**. Use **Customer-facing update** during a status change or **Send a progress update to the customer** while the repair is open. The customer should see it within ~30 seconds when the tracking page remains open.
8. Verify a different customer's JWT receives HTTP 404 for `GET /api/repairs/tracking/{repairId}`.

## New REST endpoints

- `GET /api/repairs/tracking` — signed-in customer's repairs across companies
- `GET /api/repairs/tracking/{repairId}` — signed-in owner only
- `GET /api/companies/{companyId}/repairs/{repairId}/manage` — private staff detail for an active company member
- `POST /api/companies/{companyId}/repairs/{repairId}/progress` — assigned employee or company manager, when repair is open
- Existing `POST /api/companies/{companyId}/repairs/{repairId}/status` now accepts optional `customerNote` in addition to `note`, which remains **private**.

### Example: publish a customer-visible progress update

```
POST /api/companies/1/repairs/5/progress
Authorization: Bearer <staff_access_token>
Content-Type: application/json

{"customerNote":"The replacement screen has arrived and testing is in progress."}
```

### Example: update repair status

```
POST /api/companies/1/repairs/5/status
Authorization: Bearer <staff_access_token>
Content-Type: application/json

{
  "status":"IN_PROGRESS",
  "note":"Internal technician observation — customers won't see this",
  "customerNote":"Our technician started working on your device."
}
```

## Security and behavior

- The contact name and phone from the existing repair request are not used to authorize viewing repairs; guessing a phone number or repair ID cannot retrieve details.
- `customer_note` is nullable and old private notes are not copied into it. Earlier repair status events remain visible as milestones without internal notes.
- No charges or internal costs are revealed through the tracking API, except the customer's current total charge (which may still change). No payment verification or refund handling is added.
- The signed-in account that created the repair owns its tracking access. A separate account for the contact person, guest tracking links, SMS/WhatsApp notices, and a release-to-contact consent flow are separate features.
- `LocalDateTime` timestamps in this project are recorded in UTC and the Angular tracker explicitly parses them as UTC for display in the browser's time zone.

## Verification

- Static consistency and Java/TypeScript syntax checks were performed.
- Full Maven tests, MySQL migration and Angular build need to run in the target environment; Maven dependencies and database are not available in this container.
