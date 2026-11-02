# Verified Fit Search — Customer Store and Manage Inventory

This upgrade adds an exact compatibility finder to both Angular pages:
- `frontend/src/app/shop.page.ts` — Customer Store (clients only)
- `frontend/src/app/inventory.page.ts` — Store Inventory (owner/manager only)

The fit finder uses **device type → brand → exact model**. The model field supports autocomplete from saved compatibility records **and accepts a manually typed model** to check models that are not listed. Only items with an **explicit entry for that exact type/brand/model** are returned. A name, product category, or generic `*` record is not enough to claim a verified fit.

## Installation

1. Back up your Angular project.
2. Extract the patch ZIP into your project root (`F:/repairProjectFromScratch`, or wherever `frontend/` is located). Preserve the folder paths.
3. Restart the Angular development server if necessary:

   ```bash
   cd frontend
   npm install
   npm run build
   npm start
   ```

4. Open **Customer Store** as a Client account and **Store Inventory** as a manager/owner; look for **Find verified fit / Search verified fit**.

No Spring Boot code change or Flyway migration is required: existing product APIs already include `compatibleDevices`. Backend remains at port **8081**.

## How to test

1. In Store Inventory, create or edit a product and add an exact compatible record such as **Phone / Apple / iPhone 13**.
2. Search Phone → Apple → iPhone 13. The part should be visible with an **Exact fit recorded** label in both views.
3. Search Phone → Apple → iPhone 14. The item should **not** appear unless the item also has that exact record.
4. For a part classified primarily as `Phone / Universal` but with a recorded `Phone / Apple / iPhone 13` entry, verify that it still appears for iPhone 13.
5. An item with no compatibility records—or only a `*` wildcard—does not appear as exact-model verified.
6. Change search or type/brand/model and check pagination resets to page 1. Clearing the fit finder restores general browsing.

**Compatibility disclaimer:** “Verified fit” here means an exact model-level match **recorded by the store**. It is **not independent manufacturer or supplier verification**. Confirm part number, device variant, connector, and revision before installation or sale.

## Files added/changed

- `frontend/src/app/verified-fit.ts` — shared model-specific matching and filter suggestions
- `frontend/src/app/shop.page.ts` — customer fit finder, filtered cards/table, match labels
- `frontend/src/app/inventory.page.ts` — manager fit finder, filtered stock list, match labels
- `frontend/src/styles.css` — responsive fit finder styles

No database changes. No changes to user roles, store permissions, cart, order checkout, or profit calculations.
