# Delivery Checkout, Quantity Editing & Printable Invoices

This feature extends the **latest client-only shopping** edition. It adds a mandatory delivery address and location pin at checkout, editable cart quantity before checkout, a private order dashboard for company owners/managers, and printable invoices with a separate confidential profitability copy. The Spring Boot API still runs on port **8081** and Angular on **4200**.

## Installation

1. Back up MySQL and your project, then extract the **patch** into the project root (or open the complete project ZIP).
2. Flyway runs `V7__order_delivery_location.sql` at startup. **Do not apply the migration manually and through Flyway.** Historical orders keep NULL delivery data and remain readable.
3. Start Spring Boot with your existing `DB_PASSWORD` and `JWT_SECRET_BASE64` environment variables; run `mvn test` then `mvn spring-boot:run`.
4. Run `cd frontend && npm install && npm start`. Open http://localhost:4200.

## Customer workflow

1. Log in with a standalone **CLIENT** account (no active company membership), open **Customer Store**.
2. Set quantities using the **Update** button next to every cart item. Removal works as before. Cart totals update from server values.
3. Fill delivery phone, street/building, city, country, optional postal code/instructions.
4. Click **Use my current location** (browser permission required, works on localhost or HTTPS), or enter coordinates manually. Open **Verify pin** to check the location on OpenStreetMap. **Both latitude and longitude are required**.
5. Place order: the server revalidates stock under locks, creates the order and price snapshots, stores the delivery address and map coordinates, deducts stock, records stock movements and empties the cart in one transaction.
6. Open **Orders & Invoices** to view own orders. Issued/paid invoices can be printed; no cost or margin data is exposed to customers.

## Owner/Manager workflow

1. Log in and choose your private workspace company, then select **Orders & Invoices**.
2. View the order address, phone number, delivery instructions and **Open delivery pin on map**.
3. Review the manager-only **gross profit** calculated from selling/cost price snapshots (revenue minus acquisition cost). This is **not** realized net profit.
4. Mark a confirmed order as **fulfilled** when it has actually been fulfilled. This is not proof of payment.
5. Enter the correct tax *amount* and create a draft invoice. Choose its payment due date and **Issue invoice**.
6. Use **Print customer invoice** for a standard copy, or **Print internal profit copy** for a copy with an additional clearly labeled **INTERNAL ONLY** cost/profit page. Customers are never given the internal endpoint.
7. Users can choose **Save as PDF** through their browser's print dialog. There is no server-generated PDF and no payment-gateway integration.

## API changes

| Endpoint | Who | Purpose |
| --- | --- | --- |
| `PUT /api/companies/{companyId}/cart/items/{itemId}` | Client | Save quantity before checkout |
| `POST /api/companies/{companyId}/checkout` | Client | **Now requires JSON checkout delivery details**, previous `{}` payloads return 400 |
| `GET /api/companies/{companyId}/orders/mine` | Client | Only own order details and delivery |
| `GET /api/companies/{companyId}/orders` | Owner/Manager | Company-wide order list, including delivery coordinates |
| `GET /api/companies/{companyId}/orders/{id}/profit` | Owner/Manager | Internal acquisition costs and gross profit |
| `POST /api/companies/{companyId}/orders/{id}/complete` | Owner/Manager | Mark fulfilled |
| `POST /api/companies/{companyId}/invoices` | Owner/Manager | Draft after order completion |
| `POST /api/companies/{companyId}/invoices/{id}/issue` | Owner/Manager | Issue invoice |
| `GET /api/companies/{companyId}/invoices/mine` | Client | Issued/paid invoices |

See `http/order_delivery_invoice.http` for requests. Client names and emails are taken from authenticated user accounts and snapshotted at order creation; the customer cannot submit arbitrary billing identity through checkout.

## Security and accounting notes

- A delivery coordinate is **sensitive personal information**. It is present only in owner/manager or owning-customer order endpoints, never the public store. Limit data retention, use HTTPS and secure database backups.
- Only standalone CLIENT accounts can change carts and check out. Employees, Managers and Owners **cannot place orders**.
- Never print the internal profit copy for clients: it exposes purchase costs. The backend requires company-manager/owner membership for the `/profit` endpoint.
- Gross profit excludes shipping/delivery costs, platform commissions, refunds, other expenses and tax effects.
- The invoice printout includes the company's stored contact details, but **is not automatically a legally compliant tax invoice**. Seller registration/tax identifiers, numbering, tax rules and jurisdiction-specific fields require separate review and implementation before real commercial use.
- Stock/price snapshots are immutable after checkout. To change items after ordering, cancel the confirmed order (before completion) and create another, so stock movements and financial records remain consistent.

## Verification

Java syntax and Angular TypeScript syntax were checked; ZIP integrity was verified. **A real Maven compilation, Angular production build, browser interaction test, and MySQL migration execution have not been run here.** Run them locally before deploying.
