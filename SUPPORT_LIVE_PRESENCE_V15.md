# V15 — Live support availability and offline customer inquiries

Based on the `repair_management_support_about_v14_complete.zip` delivered previously, which itself was based on the uploaded `repairAndEcommerce-main.zip`. The V15 patch intentionally does not replace the project-wide Angular folder structure, payments, or inventory features.

## Behavior

- A shop is **online for live support only while at least one authorized OWNER, MANAGER, or EMPLOYEE has explicitly selected "I am available for chat"** on the Support page.
- Staff browsers send authenticated heartbeats every 25 seconds **while the Support page is visible**. The server expires presence after 75 seconds without a successful heartbeat, so browser crashes, sleep, and lost connections do not leave staff permanently online. Each browser tab has its own session ID; separate tabs are counted as a distinct session for the same staff user but the availability count counts distinct staff users.
- Staff can turn off availability. Navigating away attempts to mark their tab offline; the 75-second expiry remains a safety net. A staff member being logged in elsewhere does *not* automatically indicate they are available for live support.
- A client sees Online/Offline status for the selected shop. The server returns **HTTP 409** if they try to start a live conversation or send a live message while no active staff heartbeat exists. Past live messages remain readable while offline.
- While offline, the client submits a persistent **offline inquiry**. The shop's owner, manager, and employee can all read offline inquiries and write one reply per inquiry, even while not marked available for live chat. The customer can view the reply later under **My offline inquiries**.
- The shop's staff roles are checked server-side for every privileged action; a customer may read only their own offline inquiries and their own live conversation. Employee staff may read/reply to live conversations but only an OWNER or MANAGER may change their OPEN/CLOSED status.
- Live chat still polls every 12 seconds and is not a WebSocket product. No push notifications, email, or external chat provider are included.

## Database migration

`src/main/resources/db/migration/V15__support_presence_and_offline_messages.sql` adds:

- `support_agent_presence`: company, staff user, browser session, server-generated expiry. An hourly job removes old sessions.
- `support_offline_messages`: customer, company, text, timestamps, optional staff response, and optimistic-lock version.

Existing `support_conversations` and `support_messages` from V14 remain untouched. Do NOT edit migrations V1–V14 once applied. If you already have a V15 Flyway migration in your actual database, assign this script the **next unused version before executing it**. Back up the production database first.

## API (Bearer JWT required)

All endpoints are prefixed `/api/companies/{companyId}/support`.

| Method | Endpoint | Permissions / purpose |
|---|---|---|
| GET | `/availability` | Client or staff: `{ "online": true, "availableAgents": 2 }` |
| POST | `/presence/heartbeat` | Staff: `{ "sessionId": "123e4567-e89b-12d3-a456-426614174000" }` |
| DELETE | `/presence/{sessionId}` | Staff: mark their own tab offline |
| GET | `/offline-messages/mine` | Client: own inquiries and any replies |
| POST | `/offline-messages` | Client: `{ "body": "Can you repair my phone?" }` |
| GET | `/offline-messages` | Owner, manager, employee: shop inbox (last 100) |
| PATCH | `/offline-messages/{messageId}/reply` | Owner, manager, employee: `{ "body": "Yes, we can." }` |
| POST | `/conversations` | Client: live chat; HTTP 409 when shop offline |
| POST | `/conversations/{conversationId}/messages` | Client or staff: live message; HTTP 409 when shop offline |

The original V14 message/history/status endpoints still work. Offline messages are limited to 2,000 characters; replies are limited to 2,000 characters.

## Local verification

1. Apply this patch to the **same local source version** that already contains V14. Review local modifications before overwriting.
2. Test backend on Java 21: `mvn clean test`.
3. Test frontend with compatible Node/Angular: `cd frontend && npm ci && npm run build`.
4. Test two users in separate browsers: manager toggles online → client starts live chat → manager goes offline → client cannot send live but can leave an offline inquiry → employee opens the shop Support Inbox, selects Offline inquiries and responds → client sees the reply.
5. Also test permissions across two companies, stale presence after a browser disconnect, two tabs for a single staff member, and a staff account removed from its company.

## Production deployment (VPS with Docker)

1. Back up the MySQL Docker volume/database, ensure migration V15 is unused, and keep `.env` and JWT secrets unchanged.
2. `cd /home/techApp && git pull --ff-only origin main`
3. `docker compose up -d --no-deps --build backend frontend`
4. `docker compose logs --tail=100 backend && docker compose ps`
5. Test as a client + owner/manager + employee on HTTPS (`wolftech.space`).

Do not use `docker compose down -v`: it deletes volumes. Deploy during a quiet window because the backend/frontend containers are recreated.

## Limitations / follow-ups

- Availability reflects **explicit willingness to answer live chat**, not raw logged-in presence. Browser disconnect detection can take up to roughly 75 seconds.
- Inquiries are stored for later retrieval in the app; there are no email, push, or SMS notifications. Staff need to open Support Center to check for inquiries.
- Plan production rate limiting, spam protection, retention/deletion rules, and reporting for personal data before broad launch.
- If you need multiple follow-up messages on an offline inquiry, that is a separate ticket-thread enhancement; this version supports a single staff reply per offline inquiry, and clients can create additional inquiries.

## Validation status

The new and modified Angular TypeScript files were syntax-transpiled, and static file/migration/ZIP checks were completed. A full Spring Boot/Maven build and Angular template compilation have **not** been executed in this environment. Do not mark this as production-ready until those pass in your PC/VPS builds and authorization paths are tested.
