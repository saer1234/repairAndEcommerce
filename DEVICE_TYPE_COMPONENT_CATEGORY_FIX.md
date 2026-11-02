# Device type vs component category — search & verified fit correction

This patch fixes the two dropdown families in **Customer Store** and **Manage → Store Inventory**.

| Field | Correct example | What it represents |
|---|---|---|
| Device type | Phone | Kind of device being repaired |
| Brand | Samsung | Device manufacturer |
| Model | Galaxy S24 | Exact device model |
| Component category | Screen | Type of replacement part |

## What changed

- Store search and Inventory search have a separate **Component category** filter.
- Verified Fit device type is derived from actual model compatibility records, but suppresses obviously misclassified component categories such as `Screen` or `Battery`.
- The Add/Edit Item screen saves its **Component category** independently, rather than copying `deviceType` into `category`.
- Spring Boot no longer overwrites the category when setting device classification.
- New or imported products using `Screen` or `Battery` as `deviceType` are rejected with an explanatory validation message. The importer requires the `Device type` column separately from `Component category`.
- Search keywords now match component categories too. Existing search, pagination, hide/delete and access rules remain.

## Apply the patch

1. Back up your source tree and MySQL database.
2. Extract the `com.example.repairProject` patch into the project root. It is for your actual Java package name. Merge with local edits if necessary.
3. Restart Spring Boot and Angular.
4. Check the client store and manager inventory. Filter **Phone → Samsung** and **Component category: Screen/Battery**.
5. In Verified Fit, choose **Phone → Samsung → Galaxy S24**. Only items with an explicit compatible record for Galaxy S24 should match.

```bash
mvn test
mvn spring-boot:run
```

```bash
cd frontend
npm run build
npm start
```

## Existing data

No Flyway migration is needed. The MySQL `items` table already has separate `category` and `device_type` columns, and `item_compatibilities` already has `device_type`, `device_brand`, and `device_model`.

However, **existing incorrect records are not automatically repaired**. Run `REVIEW_EXISTING_DEVICE_TYPES.sql` to identify them. If an item's Device Type is `Screen` or `Battery`, open it in Manage Inventory and correct both its primary classification and each compatibility entry; ensure the true compatible device/model is verified. A screen might be a phone part, tablet part or another component, so an automatic conversion to Phone would be unsafe.

Existing item categories copied as `Phone` or `Tablet` display as **Uncategorized** until you assign the correct component category by editing the item. Existing product search still includes those products; unsupported type labels are omitted from device dropdowns.

## Creating an item

```json
{
  "sku": "SAM-S24-SCR",
  "name": "Samsung Galaxy S24 replacement screen",
  "description": "Example entry; verify exact fit before selling",
  "category": "Screen",
  "deviceType": "Phone",
  "deviceBrand": "Samsung",
  "compatibleDevices": [
    {"deviceType": "Phone", "deviceBrand": "Samsung", "deviceModel": "Galaxy S24"}
  ],
  "costPrice": 50.00,
  "sellingPrice": 90.00,
  "openingStock": 5
}
```

This example is sample data. **Recorded compatible models are not independently certified** and must be validated against part specifications before a real repair.

## Verification

The shared TypeScript matching helpers compiled with `tsc` and passed focused assertions; the backend Java helper compiled with `javac`. The source ZIPs were checked for integrity. Full Maven, Angular template compilation, and local MySQL end-to-end behavior have not been verified here.
