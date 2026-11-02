# Client-only Store & Checkout — Access Rules

The storefront, cart and new checkout operations are intended **only for `CLIENT` accounts with no active company membership**. Owners, managers, employees, platform administrators, and unmigrated legacy `USER` accounts cannot place orders. Company owners and employees retain access to private inventory and order fulfillment; the public company directory remains visible without requiring shopping permission.

## Back-end rules

* `ShoppingEligibility.requireClient` checks the authenticated account's **current** `platformRole` in MySQL and refuses any active `company_members` entry. The check applies to **every company**, not just the shop currently selected.
* `GET /api/companies/{companyId}/cart`, `PUT /api/companies/{companyId}/cart/items/{itemId}`, and `DELETE` of cart items require Client access.
* `POST /api/companies/{companyId}/checkout` checks eligibility **before** creating orders or deducting stock.
* HTTP-layer `ROLE_CLIENT` restrictions provide a second defense, but membership checks are essential because an employee may still have `CLIENT` as their account type.
* History of already-placed orders, staff's order-management endpoints, and customer cancellations of earlier orders are **not deleted or blocked** by this change.
* Public product catalogue endpoints are still readable so item browsing and company discovery remain possible; no non-Client can order.

## Angular rules

* The sidebar's **Customer Store** link and customer-store selector show only to eligible Client users.
* The `/shop` route checks the current company membership list with `GET /api/companies` (not a stale UI role) and redirects staff / owners to the dashboard.
* Shopping methods check eligibility before API calls, and the backend independently returns `403 Forbidden` on disallowed cart/checkout operations.
* Owners, managers and employees continue to have access to the **Companies** directory, **Manage My Store**, **Repair Workspace**, and order administration according to their existing permissions.

## Install

Extract the patch archive into the **root of your latest project** (the folder with `pom.xml` and `frontend/`), keeping folder structure. No Flyway migration is needed. Run the backend and Angular app again.

```bash
mvn test
mvn spring-boot:run
# in a second terminal:
cd frontend
npm install
npm start
```

Backend: `http://localhost:8081`; Angular: `http://localhost:4200`.

## Verify using Postman or browser

1. Log in as a standalone `CLIENT` with no company memberships: Store link appears, adding to cart and checkout work.
2. Log in as `OWNER`, `SUPER_ADMIN`, `USER` or as an account with an active `EMPLOYEE` or `MANAGER` membership: Store link is hidden and direct `/shop` navigation redirects to Dashboard.
3. For each disallowed role, send authenticated requests directly to `GET /api/companies/{id}/cart`, `PUT /api/companies/{id}/cart/items/{itemId}`, and `POST /api/companies/{id}/checkout`: expect `403 Forbidden`.
4. Assign a Client user as an Employee: previous JWT / account type alone must not permit checkout or cart writes.
5. Log in as a manager: inventory management and existing order-list/fulfillment endpoints should continue to work.

**Validation status:** Java and TypeScript syntax, ZIP integrity and source consistency can be checked here; running Maven, browser tests, and MySQL integration depends on your local environment.
