# Item Details & Full Description — Angular frontend upgrade

This upgrade adds a shared read-only product detail panel on both **Customer Store** and **Manage / Store Inventory**. It uses existing API responses; there is **no database migration and no backend change**.

## Installation

1. Back up your Angular source files.
2. Extract `repair_management_item_details_patch.zip` into your **project root** (the directory containing `frontend/`). Keep folder names and overwrite the four changed Angular files.
3. From `frontend/`, run `npm install` if necessary, followed by `npm run build` and `npm start`.
4. Browse to `http://localhost:4200` (Spring Boot still runs on port 8081).

## What's new

- **Customer Store**: a “View full description” button on each product card and a “Details” button in table view. The panel shows full description, image, SKU, category, device type/brand, explicitly recorded compatibility, sale price, and availability. No cost or margin fields.
- **Manager / Owner Inventory**: a “Details” button in desktop table and mobile cards, including archived items. The panel includes all product details plus current stock, acquisition cost, potential gross profit/unit, and shop visibility status.
- Both panels are mobile responsive, allow closing using the button or Escape key, preserve paragraph breaks, and return focus to the clicked button on closing.
- The existing **Description** field under Add/Edit Item controls the text shown to customers and managers. If empty, the panel says “No description added yet.”

## Technical design

- New: `frontend/src/app/item-details.component.ts`
- Modified: `frontend/src/app/shop.page.ts`, `frontend/src/app/inventory.page.ts`, and `frontend/src/styles.css`
- Uses already-existing `description` data from `PublicItemView` and `ManagerItemView`, so no new endpoint or migration is needed.
- Company and role access control is unchanged; customer data does not contain `costPrice` or `potentialGrossProfitPerUnit`.

## Quick verification

1. Log in as a Client. Open Customer Store, click **View full description** or switch to Table then **Details**. Confirm selling price and description show, but acquisition cost and profit do not.
2. Log in as Owner/Manager. Open Manage → Store Inventory and click **Details**. Confirm acquisition cost, stock, and gross profit appear.
3. Edit the item's Description, save, reload, and verify the new text on both sides.
4. Test on a narrow phone browser and press Escape to close.

The files passed TypeScript *syntax parsing* and ZIP integrity checks. Full Angular compilation and end-to-end browser tests were not available in this runtime; run `npm run build` locally.
