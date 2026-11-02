# Repair Workspace — Compatible Stock Search

This update makes it easier for an assigned technician, owner or manager to identify which stocked components match the device being repaired.

## What changes

- The repair's saved device type, brand and model are used automatically; no need to re-enter them.
- **Exact recorded fit** (default): only in-stock, active products with an exact device type + brand + model entry are listed. This is a store-entered compatibility record, not a manufacturer certification.
- **Include parts that need verification:** also shows products without compatibility records or generic wildcard compatibility. The technician must tick a physical-fit confirmation box before adding such a part.
- Products with explicit compatibility records for a different model are excluded.
- Search by item name, SKU and component category; optionally choose Screen, Battery, Charging Port, etc.
- Displays available stock quantity and counts for exact matches versus items needing verification.
- Stock deduction and audit logs remain handled by the existing Spring Boot repair-parts API. Only assigned technicians or authorized managers can add parts.
- The stock API now includes the component `category` (no acquisition cost is exposed to employee roles).

## Install

1. Back up your project.
2. For a project using the Java package `com.example.repairProject`, extract `repair_workspace_compatible_stock_example_package_patch.zip` into the project root, preserving folder paths.
3. For `com.repairmanagement.repairproject`, use `repair_workspace_compatible_stock_patch.zip` instead.
4. Start Spring Boot on port 8081 and Angular on port 4200.
5. Open Manage → Repair Workspace, choose an in-progress repair with a recorded brand/model and an assigned technician.
6. Verify the exact stock-match count, try a component filter, and select a part. Only using a part deducts stock.

## Modified files

- `frontend/src/app/repair-workspace.page.ts`
- `frontend/src/app/models.ts`
- `frontend/src/styles.css`
- `src/main/java/<your package>/inventory/StockItemView.java`

No new SQL/Flyway migration is required. Existing items need correct, exact `compatibleDevices` records to appear in the default matching view. If a repair lacks a device brand or model, complete the repair device details before trusting its fit results.

## Checks performed

- Parsed all 28 Angular TypeScript files successfully.
- Ran standalone matching checks covering exact fit, incompatible model, generic wildcard, and no compatibility information.
- Verified ZIP integrity and relative paths.

**Not verified:** Full Angular template compilation, full Maven compilation, or live MySQL integration, which must be run locally.
