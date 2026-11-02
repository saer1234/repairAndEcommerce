# Stage 10 — Bulk Inventory Import + Paginated Store Listings

This update applies on top of V1–V9 and does **not** require a database migration.

## What changed

- Owner/Manager **Store inventory** now has a CSV/XLSX uploader with **Preview and validate** followed by **Import**.
- Imports create items and opening-stock audit movements through the existing item service.
- SKU duplicates already in the company are **skipped and counted**, never restocked. Repeated SKUs in the file produce a row error. Any other invalid row aborts the entire import.
- Only owners/managers can call import endpoints; customers/employees cannot import products.
- Inventory table and mobile cards share pagination: 10, 20 or 50 items/page.
- Customer store has a Card/Table display switch and pagination: 8, 12 or 24 items/page. Searches and device-type/brand filters apply **before** pagination. Customers never see cost/profit columns.
- Both pages keep existing company scoping and roles.

## Install

1. Back up your backend and frontend folders. Extract the **patch** over the project root, preserving the paths.
2. If your Java package is `com.example.repairProject`, use the separate **example package patch** instead of the standard patch. Do not mix package paths.
3. Maven dependencies added to `pom.xml`: `commons-csv` 1.14.1 and `poi-ooxml` 6.0.0.
4. Keep Spring Boot on port **8081** and Angular on **4200**.
5. Start the backend using `mvn clean test` then `mvn spring-boot:run`.
6. In `frontend`, run `npm install` then `npm start`.
7. Sign in as Owner/Manager, select **My workspace → Store inventory**, upload a CSV or XLSX, click **Preview and validate**, and then **Import**. Existing items can still be edited/restocked normally.
8. Sign in as Client and check **Customer store**; change page size and switch between Cards and Table.

## Import file columns

The bundled template is `frontend/public/assets/inventory_import_template.csv`; the Angular view exposes **Download import template**.

Required:
- `sku` (max 90), `name` (max 180), `deviceType`, `deviceBrand`
- `costPrice`, `sellingPrice` (nonnegative amounts with up to two decimals)
- `openingStock` (nonnegative whole number)

Optional:
- `category` (defaults to the device type)
- `description` (max 2000)
- `compatibleDevices` (semicolon-separated triples: `DeviceType|Brand|Model;DeviceType|Brand|Model`)

The importer also accepts the column names in the previously created **27 phone components** sample Excel/CSV file, including `Product name`, `Cost (€)`, `Selling (€)`, `Opening stock`, `Component category`, `Brand`, and `Compatible models`. For simple entries such as `Apple iPhone 13`, the brand prefix is removed. Generic phrases such as `Most USB-C devices` are **not** treated as verified model compatibility.

Only `.csv` and `.xlsx` files up to 2 MB and up to 500 nonempty data rows are accepted. Image files are not imported; upload product photos separately.

## APIs

Requires Owner/Manager Bearer token:

```
POST /api/companies/{companyId}/items/import/preview
POST /api/companies/{companyId}/items/import
Content-Type: multipart/form-data
file: your file
```

Preview response includes `totalRows`, `validRows`, `skippedExisting`, `errors` (`row`, `message`), and `sampleSkus`. The import returns `created` and `message`.

The server revalidates all rows immediately before import. Batch writing is **transactional**—if any unexpected item save fails, previously saved rows from that request are rolled back. Be aware that concurrent inventory changes may still require retrying.

## Troubleshooting

- `401`: log in again (Bearer access token).
- `403`: account lacks Owner/Manager membership in that company.
- `400`: inspect Preview errors or uploaded file format; import aborts when a row is invalid.
- `409`: another request created one of the same SKUs; preview again.

## Testing limits

TypeScript syntax checks and archive integrity checks passed. The project **has not been run** against a live MySQL server or through full Maven/Angular builds in this environment. Run the commands above locally and inspect the first import and pagination changes before production use.
