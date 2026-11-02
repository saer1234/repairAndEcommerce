# Stage 11 — Manual Whish-to-Whish transfers (no Merchant ID)

This is **not** a payment gateway. The website does not send money, access Whish wallets, or validate transfer references with Whish. It provides payment instructions and bookkeeping while your shop verifies incoming funds **inside its Whish account**.

## Install

1. Back up MySQL and your project; update the existing project, do not replace your configured `application.properties` or credentials.
2. Merge the Stage 11 files; leave `V1`–`V10` unchanged and add `src/main/resources/db/migration/V11__manual_whish_wallet_transfers.sql`.
3. Run `mvn clean test` and `mvn spring-boot:run` (backend is configured for port `8081`).
4. Run `cd frontend && npm install && npm run build && npm start`.
5. Verify the migration using `SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;`.

**Database:** MySQL 8.0.16 or later required for enforced CHECK constraints. V11 adds `companies.whish_wallet_phone`, the `whish_transfers` audit table and the `WHISH_TRANSFER` value to the payment-method CHECK constraint. Do not rerun the same migration manually after Flyway runs it.

## Configure a receiving Whish wallet

1. Sign in as Owner or Manager, select **My company workspace → Orders & Invoices**.
2. In **Whish receiving wallet & verification**, enter the company's genuine Whish wallet mobile number, e.g. `+96170123456` (example only, not a real receiving account).
3. Save. There is no real wallet verification in this application. Make sure the wallet is authorized for your business use.

## Customer pays for an order

1. Client adds products, selects delivery and completes checkout. Stock is already reserved/deducted by the preexisting checkout logic.
2. The payment panel appears immediately after checkout and within **My Orders**.
3. Client opens Whish, checks recipient name, sends **the exact amount and currency** displayed, and submits the **actual Whish transaction reference**.
4. The system records the transfer as `PENDING`; **neither the order nor an invoice is marked paid**.
5. Owner/Manager checks the receiving wallet externally: amount, currency, reference, recipient and settled incoming funds. Only then clicks **I verified receipt**.
6. If the order has no issued invoice, the approved transfer remains in an advance/verified state; when the order is fulfilled and an invoice is issued, a `WHISH_TRANSFER` payment is recorded **once**. Additional invoiced tax may still be outstanding.
7. The client can monitor the result under **My Orders**. If the transfer was rejected, they can submit a replacement reference (after checking with the company; don't resend funds automatically).

## Client pays for a repair

1. Technician completes the repair; manager creates and issues its invoice.
2. Client opens the repair invoice in **Track My Repairs**, sees the Whish widget, sends money externally, and submits the reference.
3. Manager verifies in Whish and approves; an actual `payments` row is then recorded and the invoice marked `PAID` only when its full amount is covered.

## API endpoints

| HTTP | Endpoint | Access |
|---|---|---|
| PUT | `/api/companies/{id}/whish/settings` | Owner/Manager |
| GET | `/api/companies/{id}/whish/settings` | Owner/Manager |
| GET | `/api/companies/{id}/orders/{orderId}/whish/instructions` | Client who owns order / Owner/Manager |
| POST | `/api/companies/{id}/orders/{orderId}/whish/transfers` | Client who owns order |
| GET | `/api/companies/{id}/orders/{orderId}/whish/transfers` | Client who owns order / Owner/Manager |
| GET | `/api/companies/{id}/invoices/{invoiceId}/whish/instructions` | Invoice owner / Owner/Manager |
| POST | `/api/companies/{id}/invoices/{invoiceId}/whish/transfers` | Invoice owner (Client) |
| GET | `/api/companies/{id}/invoices/{invoiceId}/whish/transfers` | Invoice owner / Owner/Manager |
| GET | `/api/companies/{id}/whish/transfers` | Owner/Manager |
| POST | `/api/companies/{id}/whish/transfers/{transferId}/verify` | Owner/Manager |
| POST | `/api/companies/{id}/whish/transfers/{transferId}/reject` | Owner/Manager |

JWT Bearer authentication is required on all endpoints. The backend determines the amount and currency; the customer cannot submit arbitrary payment amounts. Duplicate company references and repeat verification are rejected. Users cannot read another company's transfers. Pending or verified transfer prevents ordinary order cancellation (refund workflow not implemented).

## Limitations and tests

* **No Whish merchant API, payment processing, or automatic verification**. A screenshot or transaction reference is not proof of receipt.
* The `WHISH_TRANSFER` payment ledger records **manager-confirmed** money only. Actual receipt is not technically proven by the software.
* Currency conversion, refund/reversal, transfer expiry, payment-service fees, merchant compliance and automatic notifications are not implemented.
* Whish-supported wallet currencies may be limited; the existing system uses each company's chosen currency. Verify that the actual wallet supports that currency before advertising payment.
* Since ordinary orders reserve stock at checkout, manual unpaid orders may tie up stock until cancelled. Add an expiry/reservation workflow for production.
* Full Maven, MySQL and Angular integration tests must pass locally before any real financial use.
