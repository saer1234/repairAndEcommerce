# Stage 9 — Device compatibility and assigned-technician stock actions

## Requirements
- Spring Boot backend on `http://localhost:8081`, Angular on `http://localhost:4200`, MySQL.
- Apply Flyway migrations **V1 through V8** first. This patch adds **V9** only.
- Back up the database and the project before merging the patch.

## What's implemented
1. Company inventory items now have `deviceType` (category) and `deviceBrand` (sub-category). The original `category` field is retained for existing code, and is set to deviceType for newly created or edited items.
2. `item_compatibilities` records allow **one SKU** to fit several `{deviceType,deviceBrand,deviceModel}` combinations, including across brands and device types.
3. Owner/Manager inventory editor: add/remove compatible models, filter by type/brand, search SKU/name/models, edit fitment lists. Customer shop: filters by type and brand, displays verified fitment and unverified legacy items.
4. In Repair Workspace, the assigned technician (and the company owner/manager) can select applicable parts and record usage during DIAGNOSING, WAITING_PARTS or IN_PROGRESS. The existing transactional stock lock and `REPAIR` movement are retained.
5. An added part may be **returned** to inventory during those same active statuses; a `RETURN` stock movement records actor, time, item and quantity. A repair's completed/delivered state is not editable this way.
6. Where compatibility is specified, a mismatching repair device is rejected by the backend with HTTP 409. Parts with no compatibility records (e.g. old V8 products) require the technician to check an explicit confirmation box before use; this is not automatic certification that the part fits.
7. Clients still see selling prices only; internal costs and inventory editing permissions have not been expanded.

## Install
1. Choose the correct patch variant for your existing Java package:
   - `repair_management_device_compatibility_patch.zip` for `com.repairmanagement.repairproject`;
   - `repair_management_device_compatibility_example_package_patch.zip` for `com.example.repairProject`.
2. Extract the patch over your **current project root**, preserving folders. Do not extract both variants.
3. Restart Spring Boot so Flyway runs `V9__device_compatibility_and_repair_parts.sql`.
4. Start Angular, then sign in as owner/manager and open **Store inventory** to classify products, enter fitment rows, and save.
5. Assign a technician to a repair, set its status to diagnosing or in progress, and use **Use replacement part**. Confirm unknown fitments manually. Add a part and verify stock decreases; return it and verify stock increases.

## Java and Angular run commands
```bash
mvn clean test
mvn spring-boot:run
# separate terminal
cd frontend
npm install
npm start
```

## Example add-item payload
The following compatibility is **illustrative only**. Verify real manufacturer part numbers before adding a model as compatible.
```json
{
  "sku": "LCD-DEMO-001",
  "name": "Replacement screen demo",
  "description": "Example listing: fitment requires physical verification",
  "costPrice": 40.00,
  "sellingPrice": 70.00,
  "openingStock": 6,
  "category": "Phone",
  "deviceType": "Phone",
  "deviceBrand": "Samsung",
  "compatibleDevices": [
    {"deviceType":"Phone","deviceBrand":"Samsung","deviceModel":"Galaxy S23"},
    {"deviceType":"Phone","deviceBrand":"Samsung","deviceModel":"Galaxy S24"}
  ]
}
```
Add via `POST /api/companies/{companyId}/items` (owner/manager JWT).

## Repair endpoints
- `POST /api/companies/{companyId}/repairs/{repairId}/parts` — body:
  `{ "itemId": 12, "quantity": 1, "confirmUnverifiedCompatibility": false }`
- `DELETE /api/companies/{companyId}/repairs/{repairId}/parts/{partId}` — returns one recorded part line to stock before repair completion. Use the `partId` from the repair response, NOT `itemId`.
- `GET /api/companies/{companyId}/inventory/items` — owner/manager view with classification, fitment, costs.
- `GET /api/companies/{companyId}/inventory/stock` — employee stock view, with fitment and quantity but **no cost price**.

## SQL verification
```sql
USE repair_management;
SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id,sku,device_type,device_brand,stock_quantity FROM items;
SELECT item_id,device_type,device_brand,device_model FROM item_compatibilities ORDER BY item_id;
SELECT item_id,movement_type,quantity_change,performed_by_user_id,note FROM stock_movements ORDER BY id DESC LIMIT 15;
```

## Important limitations
- Existing inventory has no verified compatible devices until someone edits its records. The migration keeps those records and sets device type from the existing V8 category and brand to Universal.
- Fitment checking compares normalized device types (Phone/Mobile/Smartphone) and case-insensitive exact brand/model names, or explicit `*` wildcards. It is not an authoritative manufacturer compatibility database.
- Returning a part removes that entire recorded part-use line and restores its entire quantity; partial line returns and inventory reservations are future features.
- Full Maven builds, Angular template compilation, browser tests, and MySQL migration execution have **not been run** in this environment. TypeScript syntax and compatibility-matching behavior were checked.
