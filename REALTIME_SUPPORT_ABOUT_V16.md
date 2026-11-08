# V16: Real-time support + owner-managed About Us

This patch builds on **V15** and uses existing multi-company membership rules. No external chat server or new Angular package is required.

## Features

### Real-time messaging

- `GET /api/companies/{companyId}/support/events` streams authenticated `text/event-stream` (SSE) over HTTPS.
- Angular uses `fetch()` with `Authorization: Bearer <JWT>`; the JWT is never placed in the URL.
- The client still **sends** chat messages with the existing authenticated REST `POST` endpoint. The server pushes a `update` notification immediately after messages/conversations/offline replies commit; browsers then fetch the latest authorized conversation data.
- Each company has its own **staff** subscribers; customers receive notifications only for their own company/customer ID. The stream contains no message text or customer details.
- Existing online/offline policy stays intact: customers cannot start or send live messages without a staff availability heartbeat; offline inquiries continue to work while staff is away.
- Event streams send a keepalive every 20 seconds, reconnect automatically after network failures, and expire when the authenticating JWT expires. A 30-second presence check also detects staff lease expiry; a slower HTTP refresh acts as fallback when the stream is unavailable.
- This is a single-instance in-memory notifier. If you later run multiple backend replicas, use Redis Pub/Sub or a broker to fan out change notifications across replicas.

### Owner-managed company profile

- `V16__company_about_profiles.sql` creates `company_about_profiles` with `company_id` as a foreign key / primary key.
- `GET /api/catalog/companies/{companyId}/about` is public and returns the company's published details (or company registration contact fallbacks).
- `PUT /api/companies/{companyId}/about` allows **only that company's OWNER membership** to change headline, description, address, phone, email, map URL, opening hours, Instagram, Facebook, TikTok, WhatsApp, and website URL.
- Angular `/about` is now a dynamic public multi-company page with a shop selector and social/contact links.
- Owners can edit via `/about-settings` under the main navigation; employees, managers and other companies are blocked on the server regardless of UI state.
- URLs are validated to allow only http/https; text is rendered as text, not HTML.

## Installation

**Important**: This patch was made against the provided V15 full project ZIP, not a live checkout of your current GitHub branch. Review the modified files before applying, especially if you edited these files since V15.

1. Commit/backup your current code and **back up MySQL**.
2. Extract the patch at your project root (where `pom.xml`, `frontend/`, `src/` reside).
3. Make sure V14 and V15 Flyway migrations are already applied. Verify `V16` is unused; never overwrite applied migrations.
4. Run `mvn clean test` with Java 21 and `npm ci && npm run build` inside `frontend/` with your Angular-compatible Node version.
5. Start the API locally against your test MySQL database; Flyway creates the new table.
6. As OWNER, open `/about-settings`, select your company in the workspace, fill the contact/social details and save. Open `/about?companyId=<id>` in an incognito window to verify the public view.
7. Test one customer session and one online staff session in separate browser windows. Send a message and check it appears immediately without clicking Refresh; go offline and verify customer live send is rejected and offline inquiry works.
8. Commit and push to GitHub. On VPS, after backup, `git pull --ff-only origin main && docker compose up -d --no-deps --build backend frontend`.

No secrets, customer conversation history, or real business address/social content are included in the patch.

## Technical considerations

- SSE requires an uninterrupted HTTPS reverse-proxy stream. Caddy's `reverse_proxy backend:8081` supports this. If an external CDN/proxy buffers SSE, disable buffering for the `/api/companies/*/support/events` endpoint.
- This implementation emits a lightweight change signal, then reloads the authorized data using the normal API. This avoids broadcasting customer messages to unauthorized clients.
- Owner changes publish instantly after a normal API fetch; currently existing About pages need a refresh to show an owner edit. Real-time About updates are not required.
- This patch does not add image attachments, video/audio calls, typing indicators, message delivery receipts, or email notifications.
- Deployment was not run against your live database or VPS. Full Maven and Angular builds have not been run in this container.
