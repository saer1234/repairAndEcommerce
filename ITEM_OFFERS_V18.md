# V18 — Item discount offers

This update adds **percentage discounts** to the existing multi-company store. It is designed for the V17 project snapshot. It does not require a new public route or payment provider.

## What the feature does

- A shop's **OWNER or MANAGER** can set, edit or remove one current offer on each active item from **Store Inventory → Set offer**. Employees and clients cannot change offers.
- Discount percentage is a whole number from **1 to 90**. An offer applies immediately and continues until changed or removed. This version has **no automatic start/end schedule**, coupon codes, or discount stacking.
- The existing `items.selling_price` remains the normal/list price. The new `item_offers` table stores the current discount; the effective price is calculated on the **server**, rounded to two decimals using HALF_UP.
- Customer store cards, table, item details, cart and checkout show/use the discounted price. The original price is shown struck through.
- Checkout locks the affected item records, uses the live discount price, snapshots the discounted **unit_selling_price** onto `order_items`, and recomputes the total. Old invoices/order gross profit are not rewritten when a discount changes.
- Frontend sends `expectedTotal` during checkout; if the visible cart total has changed, backend returns **409 Conflict** instead of silently charging a higher amount. API callers using the older checkout payload still work, but should add `expectedTotal`.
- Discounts apply to **store sales**, not repair-parts pricing or labor charges. Historical order snapshots retain their actual prices.

## Migration

`src/main/resources/db/migration/V18__item_percentage_offers.sql`

Creates `item_offers` (one row per item via unique item_id) with a foreign key and percentage check. It does **not** modify historical orders or product prices. Existing items have no offer (0%).

Before deploying, back up MySQL, verify V18 isn't already used, and don't edit migrations that have been applied. Flyway picks up the new script on backend startup.

## API (OWNER/MANAGER only)

Create or change a discount:

```http
PUT /api/companies/1/items/42/offer
Authorization: Bearer YOUR_OWNER_OR_MANAGER_JWT
Content-Type: application/json

{"discountPercent": 20}
```

Remove a discount:

```http
DELETE /api/companies/1/items/42/offer
Authorization: Bearer YOUR_OWNER_OR_MANAGER_JWT
```

Both return the updated `ManagerItemView`, including `sellingPrice`, `effectivePrice`, `discountPercent`, `potentialGrossProfitPerUnit`.

`GET /api/companies/1/items` returns the public item list including `sellingPrice` (list), `effectivePrice` (sale) and `discountPercent` (0 if none). Customer prices are not trusted in requests.

## Example

An item with a list price of 120.00 and a 25% discount appears at **90.00**. A customer ordering two units sees a total of **180.00**, and those two order lines retain a unit price of **90.00** even if the owner later cancels the promotion.

## Local testing

1. Run `mvn clean test` from the repository root. New backend tests: `ItemOfferTest`, `ItemOfferServiceTest`.
2. Run `npm ci` and `npm run build` from `frontend/` with a Node version supported by your Angular CLI.
3. Create a test item (owner account) and set a 20% offer from Store Inventory.
4. Log in as a client: verify cards, details, and cart totals; place an order and confirm its saved unit price.
5. Remove the discount and verify the new cart price changes but the older order does not.
6. Log in as an employee: PUT/DELETE offer must return 403. Try using a company you do not manage, which must also be denied.
7. Confirm Whish payment requirements do not change: a transfer is not accepted until confirmed by shop staff.

## Deployment

Push the reviewed changes from the PC to GitHub, then on the VPS:

```sh
cd /home/techApp
git pull --ff-only origin main
# Back up the database before restarting a backend that runs Flyway.
docker compose up -d --no-deps --build backend frontend
docker compose logs --tail=100 backend
```

**Known limitation:** This implementation is based on the supplied V17 complete ZIP snapshot. It has not been integrated with unshared later commits; review conflicts before applying it. Maven, full Angular compilation, and live MySQL integration were not executed here.
