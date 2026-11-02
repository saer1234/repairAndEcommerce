# Add products to the store (Stage 7 inventory screen)

This addition uses the **existing Stage 3 inventory backend** and Stage 7 JWT login. No SQL migration or new Java classes are needed.

## Instructions

1. Start MySQL and Spring Boot (port 8081) with DB_PASSWORD and JWT_SECRET_BASE64 configured.
2. In `frontend/` run `npm install` and `npm start`; open http://localhost:4200.
3. Register/login, select a company you own or manage. If necessary, create one under Companies.
4. Open **Manage Inventory** in the sidebar (or **+ Add item** on Customer Store).
5. Enter SKU, name, optional description, cost price, selling price, and opening stock. Click **Add item to store**.
6. Go to **Customer Store** to see the product. Prices for customers show selling price only.

An OWNER or MANAGER can see cost prices and potential gross margin. Employees and customers cannot create items. Existing products can be edited (SKU/name/description/cost/sale price) and restocked; the stock increase is audited in `stock_movements`.

## API requests

`POST http://localhost:8081/api/companies/1/items` with Bearer token:

```json
{"sku":"SCREEN-001","name":"Samsung screen replacement","description":"Black replacement screen","costPrice":50.00,"sellingPrice":80.00,"openingStock":10}
```

Expected: `201 Created` and a response including item ID, prices, stock, and potential gross profit per unit.

- `GET /api/companies/1/items`: public browse (no cost price).
- `GET /api/companies/1/inventory/items`: manager items (cost, sale, margin and quantity).
- `PUT /api/companies/1/items/{itemId}`: manager update of SKU/name/description/prices (not stock).
- `POST /api/companies/1/inventory/items/{itemId}/movements`: audited RESTOCK increase.

Do not use the item create endpoint to restock an existing SKU. Use the stock movement endpoint.

## Testing notes

- Try duplicate SKU: should be rejected (HTTP 409).
- Try negative prices or stock: should be rejected (HTTP 400).
- Log in as a customer: the inventory screen has no editor, and the backend also rejects POST.
- Create item with opening stock 0: customer sees Out of Stock, then restock and refresh.
- Switch between companies: inventory should change with the selected company.
- Verify the `items` and `stock_movements` MySQL tables.

Current product images are not stored in the schema. An image upload/storage feature requires additional work and a new database migration. JWT is still in memory and resets on refresh.
