# V12 — Order ID / Client ID search and Whish sender details

## Features
- **Owner / Manager → Orders & Invoices:** Filter company orders by database **Order ID** and **Client ID**. Both fields allow exact matches, and can be combined. Click **Clear** to reset. An order row now displays both IDs. Search filters the company's currently loaded order list (not a server-side paginated search). Invoice actions remain attached to the selected order.
- **Client → Pay by Whish-to-Whish:** Sender name and sending-wallet phone number are prefilled from the authenticated user but may be edited if the Whish sender differs. Both are required for a new transfer reference; name/phone are only sender-reported details, not proof of payment or identity. The account owning the order/invoice remains determined from the JWT.
- **Owner / Manager → Whish transfer queue:** See authenticated client ID and registered name alongside sender-reported Whish name/phone. Verify money in the *actual* receiving wallet before approving a claim.

## Database
Flyway migration `V12__whish_sender_contact.sql` adds `sender_name` and `sender_phone` to `whish_transfers`. Existing records retain their IDs/status and are backfilled from user account details when available. The new columns remain nullable only for legacy records; new API submissions require both.

**Do not edit V1–V11.** Back up your database before starting Spring Boot and running V12. Only run Flyway against the target database (`repair_management`).

## Installation
For existing `com.example.repairProject` Java package, extract `repair_management_order_search_whish_v12_example_package_patch.zip` into the project root, keeping existing package paths. For `com.repairmanagement.repairproject`, use the regular patch. The full ZIP is the reference project.

Commands:

```bash
mvn clean test
mvn spring-boot:run
cd frontend
npm install
npm run build
npm start
```

Spring Boot remains on `http://localhost:8081`.

## Whish transfer JSON (client)

`POST /api/companies/1/orders/42/whish/transfers` or `POST /api/companies/1/invoices/17/whish/transfers`:

```json
{
  "reference": "ACTUAL-WHISH-REFERENCE",
  "senderName": "Example Client",
  "senderPhone": "+96170123456"
}
```

A signed-in client can only submit for orders/invoices that belong to their own authenticated user ID. Both fields are snapshots; the sender details do not replace the registered client identity.

## Test plan
1. Create orders from two client accounts in the same company.
2. Log in as Owner/Manager, enter the first **Order ID**; only that order should appear. Clear, then enter a **Client ID**; that client's orders should appear. Test both filters combined and with an unmatched ID.
3. Log in as Client, start a Whish transfer; check prefilled sender name and phone, edit phone if necessary, and submit with a valid reference.
4. In manager queue verify registered name / client ID as well as sender-reported name / phone.
5. Verify that blank/invalid phone/name is rejected with HTTP 400 and that mere reference submission never marks the invoice paid.
6. Verify that direct attempts to view another company's orders or Whish submissions are forbidden.

This update has **no live connection to Whish**. Receipt verification is still manual; use test accounts and development transfers until locally verified.
