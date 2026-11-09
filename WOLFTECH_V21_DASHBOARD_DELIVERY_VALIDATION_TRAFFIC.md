# WolfTech V21 — Date-Range Revenue, Delivery Checkout, Registration Validation, IT Traffic

Based on `WolfTech_V20_Complete_Project.zip`, **not** directly synchronized with a newer GitHub branch. Review before applying.

## Features

### 1. Company dashboard: revenue by date
- Owners/managers can select `From`/`To` dates on the Dashboard.
- API: `GET /api/companies/{companyId}/finance/date-range?from=2026-11-01&to=2026-11-09`.
- Backend authorizes a manager/owner company membership.
- Includes completed item sales, delivery fees, completed repair labor/parts, total recognized revenue.
- Dates use **UTC completion timestamps**, inclusive start/end dates. This is earned/fulfillment revenue, **not payment receipts**; do not mix with collected cash or profit.
- Query range limited to 366 days within the last five years to avoid unbounded reports.

### 2. Delivery / free store pickup
- Manager/owner settings in **Orders & Invoices**; flat amount in the company's currency. Default: delivery enabled, fee zero, for compatibility with existing deployments; set your intended charge before enabling live delivery.
- `PUT /api/companies/{companyId}/delivery-settings` `{ "deliveryEnabled": true, "deliveryFee": 5.00 }`.
- Client reads `GET /api/catalog/companies/{companyId}/delivery-settings` (authenticated; client access).
- Client chooses free pickup or delivery, which requires a phone/address/map pin. Choosing delivery opens a confirmation popup showing fee and final total before order creation.
- Backend independently calculates delivery fee and checks client's expected total against snapshot. Invoice gets a distinct `Delivery fee` line. Old orders keep their original snapshots.
- The term **delivery fee** is used instead of tax. Any statutory sales tax remains separate and must be set/handled in the existing invoice process as applicable.

### 3. Registration clarity
- Backend now returns meaningful `message` and per-field `fieldErrors` for validation.
- Registration page shows minimum password length, invalid email, name errors, and password-confirmation mismatch. Duplicate emails display the API's `Email is already registered` message.
- No passwords are echoed in API validation responses.

### 4. IT Traffic Monitor
- `/platform-traffic` route in shell for **SUPER_ADMIN**, backed by the protected `/api/platform/traffic/daily?date=YYYY-MM-DD` API.
- Counts HTTP API requests by IP and UTC day. It stores request counts, known request/response `Content-Length` bytes (which may be absent), last-seen time. Aggregates flush every 60 seconds; can lose up to the last minute on an unclean restart.
- IP literals only, no passwords, request bodies, query strings, or user email saved. Data retained 7 UTC days; the dashboard returns up to 500 IP rows per day. **Raw IPs are personal data**: publish a privacy notice, restrict platform-admin access, implement appropriate organizational controls and assess applicable privacy law.
- This is **HTTP/API activity only**, not total VPS inbound/outbound network bandwidth or all anonymous HTML/JS/static page visits. It also does not equate one IP to one person. Run `bash scripts/check-network.sh` on the VPS for container-level network I/O since startup, or use host network monitoring for a complete history.
- Caddy overwrites `X-Real-IP` from its immediate TCP peer; when behind a CDN, configure trusted upstream proxy ranges carefully to avoid recording CDN IPs or accepting spoofed headers.
- Backend runs in Docker private network and is not exposed publicly. The production compose variable `APP_TRAFFIC_TRUST_CADDY_REAL_IP=true` is only safe when only your trusted Caddy can reach backend.

## Database
New Flyway `V19__delivery_fees_and_daily_traffic.sql` (the next unused migration version in the V20 snapshot). **Do not alter a previously applied migration**. Check the deployed `flyway_schema_history` and choose the next unused version if your fork already has V19.

## Install
Back up database and files first. Create a new Git branch, unpack patch at project root, review changed files, then:

```
mvn clean test
cd frontend
npm ci
npm run build
```

Git commit/push, then on VPS:

```
cd /home/techApp
git pull --ff-only origin main
docker compose up -d --no-deps --build backend frontend
docker compose ps
docker compose logs --tail=100 backend
```

The last command starts Flyway V19 in the backend. Do not use `docker compose down -v` (deletes persistent database volumes).

Check a pickup order, a delivery order, an invoice with fee, range reporting, an invalid registration, and superadmin vs ordinary account traffic access before publishing to users.

## Validation notes
Static checks performed: TypeScript parse, CSS parse, Compose YAML parsing, archive integrity. **Full Maven tests, Angular template build, and MySQL integration were not run here** and must be run in your environment.
