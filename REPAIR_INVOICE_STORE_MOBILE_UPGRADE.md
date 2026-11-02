# Repair invoices, product categories, store search, and mobile UI

This upgrade is **based on the latest `repair_management_delivery_checkout_complete.zip` project**, including the existing client-only shopping, repair tracking, order checkout, and order invoices.

## Installation

1. Back up MySQL and your project. Do not reinstall Stage 1 or manually execute the Flyway migrations.
2. Extract the `repair_management_repair_invoices_mobile_patch.zip` files into your current project root, preserving folders.
3. Run Spring Boot with MySQL as before on port **8081**. Flyway applies `V8__repair_reports_and_product_categories.sql` automatically.
4. In `frontend/`, install dependencies if necessary and run `npm start`. Angular serves on **http://localhost:4200**.
5. Run `mvn test` and `npm run build` on your machine before relying on the upgrade.

## Repair invoice workflow

1. A client creates a repair, or staff select an existing registered Client account.
2. The owner/manager assigns a technician. The technician/manager publishes progress and enters replacement parts (prices are snapshotted when used).
3. The assigned technician (or manager) fills out **Diagnosis** and **Work performed**, and the manager enters the customer-facing labor charge and private labor cost.
4. The repair cannot be marked **COMPLETED** unless there is an assigned technician and a detailed report.
5. After completion, the manager opens the **Repair Invoice & Technical Report** panel. They create a draft invoice and enter the applicable tax amount, review it, and issue it.
6. The client sees the issued invoice in **Track My Repairs** and can print it. It includes the customer/device/technician, reported problem, diagnosis, work done, itemized parts and labor, subtotal, tax, and total.
7. The owner/manager sees the same customer invoice plus a separate **internal cost/gross-profit report**, which can be printed with a clearly separated confidential page. Customers never receive internal costs from the API.

The invoice preserves the technician name, diagnosis, work-performed report, and direct-cost totals at invoice creation. Repair parts already retain their historical selling and acquisition prices. No automatic payment processing is added. Gross profit is revenue before tax less direct parts and labor costs; it excludes overhead, fees, delivery, and other operating expenses.

**Legacy data:** Existing repair invoices issued before V8 cannot retroactively contain technician reports that were never collected. Existing rows remain intact. Complete any missing report details before issuing *new* invoices. For repairs completed in older versions with no assigned technician, manual data cleanup may be needed.

## New endpoints

| Method | Endpoint | Who |
|---|---|---|
| PUT | `/api/companies/{companyId}/repairs/{repairId}/report` | Assigned employee or manager |
| GET | `/api/companies/{companyId}/repairs/{repairId}/invoice-report` | Owner/manager, or the repair's client **after issue** |
| GET | `/api/companies/{companyId}/repairs/{repairId}/invoice-report/internal` | Owner/manager only |
| POST | `/api/companies/{companyId}/invoices` | Owner/manager (existing API; requires completed repair, technician/report) |
| POST | `/api/companies/{companyId}/invoices/{id}/issue` | Owner/manager (existing API) |

Example report update:

```http
PUT http://localhost:8081/api/companies/1/repairs/27/report
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "diagnosis": "The power button flex cable was damaged and intermittently shorting.",
  "workPerformed": "Replaced flex cable, cleaned contacts, and tested power cycling five times."
}
```

## Product category & store search

Each item has a `category` (up to 80 characters). All preexisting items migrate to `General`. Managers can edit categories in **Store Inventory** and search by name/SKU/description/category. Customers can now search the store and choose **All categories** or a specific category. Product costs remain hidden from public APIs.

## Mobile UI

Responsive changes cover customer store, item management, checkout and order quantities, company navigation, repair tracking, and printable repair invoices. At narrow widths, inventory lists become editable cards, product grids become a single column, forms stack vertically, and repair invoice lines use labeled rows. Browser printing supports A4/PDF-style print via the print dialog.

## Local verification checklist

- Create a new item in the `Accessories` category; search `Accessories` in the store and filter the category.
- On a ~390 px wide screen, verify that the navigation, inventory cards, cart, repair workspace, and invoices fit without horizontal page scrolling.
- Verify an unassigned or unreported repair cannot be completed.
- Save a report and technician assignment, complete the repair, generate an invoice, issue it, and check that the customer's print view shows neither purchase costs nor profit.
- With an owner/manager account, check that the internal report shows direct parts and labor costs and gross profit, and that an unauthorized Client request to `/invoice-report/internal` receives **403**.
- Verify an unrelated Client cannot open another customer's repair invoice.
- Confirm existing order invoices and delivery location handling still work.

## Verification limits

The generated Java and TypeScript were checked at source level; the project has **not been compiled with Maven or Angular nor tested with a live MySQL instance in this environment**. Full integration and browser/device testing must be completed locally.
