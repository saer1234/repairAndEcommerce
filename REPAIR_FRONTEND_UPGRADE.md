# Repair Management — Angular Frontend Upgrade

This update completes the previously missing **Stage 5 repair management interface**. It is based on the **latest image-upload-fixed Stage 7 project**, not on an older Stage 5 ZIP.

## What changed

- `/repairs` (My Repairs): new repair requests for any selected public company; per-company personal requests; detail view with timestamps, parts, customer charges and status history.
- `/repair-workspace` (Repair Workspace): company-scoped repair queue, search/status filter, details, assignment, status transitions, inventory replacement parts, labor amounts, timeline, and finalized repair gross profit.
- `CompanyMember` TypeScript response model corrected to use `role` and `userId` as returned by Spring Boot (also fixes the Companies & teams list).
- Both pages use the existing JWT session and company selection; no SQL migration or backend endpoint changes are required.

## Download / install

For an existing installation on the latest product-image-fixed release, unzip **repair_management_repair_frontend_patch.zip** into the project folder containing `frontend/`, `pom.xml`, and `src/`, preserving file paths. The patch updates:

```
frontend/src/app/repairs.page.ts
frontend/src/app/repair-workspace.page.ts
frontend/src/app/models.ts
frontend/src/app/routes.ts
frontend/src/app/shell.page.ts
frontend/src/app/companies.page.ts
frontend/src/styles.css
http/repair-frontend-jwt.http
REPAIR_FRONTEND_UPGRADE.md
```

For a clean installation, use **repair_management_repair_frontend_complete.zip** instead.

Start backend on `localhost:8081` and Angular on `localhost:4200`:

```bash
mvn test
mvn spring-boot:run

# In another terminal:
cd frontend
npm install
npm start
```

Log in through the Angular UI. Your Stage 7 session is retained on refresh in the same browser tab (subject to JWT expiry).

## Permissions

| Action | Customer | Employee | Owner/Manager |
|---|---|---|---|
| Submit a repair to a public shop | Yes | Yes | Yes |
| View their own customer repairs | Yes | Yes | Yes |
| View private company repair queue | No | Their member companies | Their managed companies |
| Change repair status | No | Assigned repairs only | Yes |
| Add replacement parts | No | Assigned repairs only | Yes |
| Assign employee | No | No | Yes |
| Enter labor charge and internal labor cost | No | No | Yes |
| See unit cost and profit | No | No | Yes |
| Mark completed repair delivered | No | No | Yes |

Spring Boot enforces these permissions. Frontend visibility is not a replacement for backend authorization.

## Backend workflows supported

- `NEW -> DIAGNOSING | CANCELLED`
- `DIAGNOSING -> WAITING_PARTS | IN_PROGRESS | CANCELLED`
- `WAITING_PARTS -> IN_PROGRESS | CANCELLED`
- `IN_PROGRESS -> WAITING_PARTS | COMPLETED | CANCELLED`
- `COMPLETED -> DELIVERED` (manager only)
- `DELIVERED` and `CANCELLED` are terminal.
- Existing backend prohibits cancellation once a replacement part has been consumed. The UI hides that transition when parts exist.
- Adding a part deducts stock and captures current item prices. It cannot be undone in this version. Confirm correct item and quantity before saving.
- The *repair profit summary* covers finalized repairs and is an estimated **gross** profit, not net cash profit or collected payments.

## Manual acceptance testing

1. Register customer, company owner, and a technician account. Add the technician via **Companies & teams** using the registered email.
2. As the customer, use the public company picker to select a shop, open **My Repairs**, create a request, and view the new detail card and timeline.
3. As the owner, select that company in **My workspace (private)**, open **Repair Workspace**, and assign the registered technician.
4. Log in as the assigned technician, open the same workspace, and change `NEW -> DIAGNOSING` with a note.
5. As technician or owner, select a positive-stock item in **Use replacement part**. Check stock decreases in **Manage My Store**.
6. As owner, enter labor charge and labor cost, and verify internal costs are not present in the customer's view.
7. Progress `DIAGNOSING -> IN_PROGRESS -> COMPLETED`; as owner, mark `DELIVERED`.
8. View the manager's finalized profit summary. Visit another company using the public store picker and verify it does **not** change the manager's private repair workspace.
9. Refresh the browser and verify the login session still exists until its token expires.
10. Verify cross-company access and unauthorized changes return 403/404 from the backend.

## Existing limitations

- Repairs cannot yet contain diagnostic photos or customer attachments.
- There is no workflow to reverse consumed repair parts; do not use cancellation as a stock-return substitute.
- The full Angular production build and MySQL runtime integration require local testing.
- This update adds no payment or invoicing UI; those backend modules remain separate.
