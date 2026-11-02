# Store Inventory — Hide / Delete / Restore (V10)

## Scope

This update distinguishes between a product being **active in internal inventory** and **visible to clients**.

- **Hide from clients** (`visible_to_clients = 0`, `active = 1`): keeps stock available to employees for assigned repairs, and keeps the item in owner/manager inventory, but removes it from the public catalog and prevents new cart quantity changes and checkout.
- **Show to clients** (`visible_to_clients = 1`, `active = 1`): returns the product to the public catalog and shopping.
- **Delete** (`active = 0`, `visible_to_clients = 0`): archives the item from active inventory and hides it from clients. This is a **soft delete** that protects existing orders, repair references, and stock movement history. Current inventory valuation excludes archived items. Old stock counts remain in the archive for reference.
- **Restore hidden** (`active = 1`, `visible_to_clients = 0`): restores the item to internal inventory but intentionally does not automatically publish it to clients.

Only authenticated **company owners/managers** may change visibility, delete, or restore their own company's products. Employees cannot use these actions.

## Install

1. Back up MySQL and your project directory.
2. Extract the correct patch at the root of your Spring Boot project.
3. Keep existing V1–V9 migration files unchanged.
4. Restart Spring Boot on port **8081**. Flyway should run `V10__item_store_visibility.sql`.
5. Restart Angular (`cd frontend && npm start`) if needed.
6. In **Store Inventory**, use **Item status** to switch between Active, Visible, Hidden, Deleted, and All. Use the actions on each product row or mobile card.

### MySQL check

```sql
SELECT id, sku, name, active, visible_to_clients, stock_quantity
FROM items ORDER BY id DESC;
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
```

## Endpoints

```http
PATCH http://localhost:8081/api/companies/{companyId}/items/{itemId}/visibility
Authorization: Bearer <owner-or-manager-JWT>
Content-Type: application/json

{"visibleToClients":false}
```

```http
DELETE http://localhost:8081/api/companies/{companyId}/items/{itemId}
Authorization: Bearer <owner-or-manager-JWT>
```

```http
POST http://localhost:8081/api/companies/{companyId}/items/{itemId}/restore
Authorization: Bearer <owner-or-manager-JWT>
```

`GET /api/companies/{companyId}/items` and the public item detail endpoint now only expose active, visible items. Existing carts containing a hidden/deleted item cannot check out until that line is removed or the item is republished. The manager inventory endpoint continues to return all items, including archived records.

## Important notes

* This is **not physical deletion**; historical records and archived SKU uniqueness are retained. To reuse a deleted SKU, restore that product instead of creating a new one.
* Hide is a storefront visibility control, not private file storage. Existing direct URLs to publicly served product images can still be requested. Do not upload sensitive images as product images.
* Existing active products default to visible on V10 migration.
* Run `mvn test`, `mvn spring-boot:run`, `cd frontend && npm run build` locally. Static checks here are not a live database or Angular compile verification.
