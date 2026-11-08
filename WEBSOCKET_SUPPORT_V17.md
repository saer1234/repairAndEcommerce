# Repair Management — V17 Bidirectional WebSocket Support Chat

**Starting point:** the previously supplied V16 complete project ZIP. If your current GitHub branch differs, merge this patch carefully; do not overwrite newer, unrelated local edits.

## What changed

- **Real WebSocket transport** (`/api/support/ws`) replaces the V16 SSE notification stream. Both *sending* and *receiving* live chat messages use WebSocket frames. The Spring service saves a message to MySQL before it is delivered.
- Customer and active shop staff receive instant `message` frames. The server uses existing `SupportService` checks: no client can read another client's conversation, no employee can read another company's conversations, and no live sending is allowed while the company has no available staff.
- Offline inquiries, conversation history, online/offline toggles, and 25-second staff heartbeats remain in REST. The WebSocket broadcasts `refresh` frames for new conversations, status changes and offline inquiries.
- Frontend automatically reconnects and reconciles chat history. If WebSockets are blocked, it can still **send** by the existing authenticated REST API and fetches updates periodically. This fallback is not instant.
- A JWT-authenticated `POST /api/companies/{companyId}/support/ws-ticket` issues a **single-use 30-second ticket**. The browser upgrades to `/api/support/ws?ticket=...`; the long-lived JWT is never put into the URL. Tickets can be consumed once, and sockets stop receiving after token expiry or when membership is revoked.
- Existing server origin checks are enforced for the WebSocket handshake. No new database table or Flyway migration is required.

## Changed paths

- `pom.xml` — add `spring-boot-starter-websocket`.
- `src/main/java/com/repairmanagement/repairproject/security/SecurityConfig.java` — allow ticket-authenticated WebSocket handshake route only.
- `src/main/java/com/repairmanagement/repairproject/support/SupportController.java` — add ticket API and publish new messages.
- `src/main/java/com/repairmanagement/repairproject/support/SupportEventStream.java` — compatibility adapter for existing REST calls.
- **New:** `src/main/java/com/repairmanagement/repairproject/support/SupportWebSocketTickets.java` — one-use ticket issuance, expiry, and validation.
- **New:** `src/main/java/com/repairmanagement/repairproject/support/SupportWebSocketConfig.java` — WebSocket handler, origin restrictions, ticket handshake.
- **New:** `src/main/java/com/repairmanagement/repairproject/support/SupportWebSocketHub.java` — bidirectional frames, DB-checked permissions, message broadcasts, timeouts and ping.
- **New:** `src/test/java/com/repairmanagement/repairproject/support/SupportWebSocketTicketsTest.java` — token mismatch and single-use tests.
- `frontend/src/app/core/services/support-realtime.service.ts` — browser-native WebSocket client, reconnect, message acknowledgement.
- `frontend/src/app/pages/support.page/support.page.ts` — live messages appended directly; fallback REST.
- `frontend/src/app/pages/support.page/support.page.html` — WebSocket connection indicator and chat scroll target.
- `frontend/proxy.conf.json` — forward WebSocket upgrades during Angular local development.
- `src/main/resources/application.properties`, `compose.yaml` — configurable allowed WebSocket origins.

## Wire protocol

1. Browser sends its normal Bearer JWT to `POST /api/companies/5/support/ws-ticket`.
2. Server returns `{ "ticket": "one-time-ticket", "expiresAt": "..." }`.
3. Browser opens `wss://wolftech.space/api/support/ws?ticket=<one-time-ticket>`; server checks origin, consumes ticket, and binds user/company authorization.
4. For a live conversation, the browser sends:

```json
{"type":"send", "requestId":"a-unique-uuid", "conversationId":42, "body":"Hello"}
```

5. Server saves using existing `SupportService.send` and acknowledges:

```json
{"type":"ack", "requestId":"a-unique-uuid", "message":{"id":123,"conversationId":42,"senderId":10,"senderName":"Example","senderType":"CUSTOMER","body":"Hello","createdAt":"2026-11-08T13:00:00"}}
```

6. Connected customer + authorized staff receive a `type: "message"` frame containing the saved message; the UI deduplicates by message ID.

## Build and test before production

From the project root:

```bash
mvn clean test
cd frontend
npm ci
npm run build
```

If you build inside Docker, the required Java and Node versions are provided by the Dockerfiles. On your VPS, after pushing reviewed source code and backing up first:

```bash
cd /home/techApp
git pull --ff-only origin main
docker compose config --quiet
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
docker compose ps
```

**No database migration.** Do not run `docker compose down -v` or reset MySQL.

### Allowed website origins

The default allowed WebSocket origins are `http://localhost:4200` and `https://wolftech.space`. If you already set an environment variable for the WebSocket origin, ensure it includes your actual HTTPS domain:

```properties
SUPPORT_WS_ALLOWED_ORIGINS=http://localhost:4200,https://wolftech.space
```

This can be placed in your VPS `.env` (which must **not** be checked into Git). The `compose.yaml` passes it to the backend. No extra open ports are needed: Caddy proxies `/api/support/ws` on the existing HTTPS connection. **Do not introduce a separate public port 8081.**

## End-to-end verification

1. Use separate browsers, one as a **client** and one as a **company owner/manager/employee**.
2. Staff selects `I am available for chat`. Client chooses the same shop.
3. In Chrome DevTools → Network → **WS**, check `GET /api/support/ws?...` returns **101 Switching Protocols**. Frames include `connected`.
4. Client starts a live chat and sends a message. The staff browser must display it without refresh. Verify the reverse direction.
5. Temporarily disconnect/reconnect a browser and verify messages reload without duplication.
6. Turn all staff offline; clients must be offered an offline inquiry and cannot send live messages.
7. Check that another client, or staff from another company, cannot see the conversation. Confirm WS stops when JWT expires or staff membership is revoked.
8. Test on mobile and under `https://wolftech.space`, not only localhost.

Troubleshooting: `403` at WebSocket upgrade usually indicates ticket/origin validation; `401` at `/ws-ticket` means the normal JWT is invalid; `502` suggests proxy/backend connectivity; no `101` usually means a WebSocket upgrade problem. Check `docker compose logs --tail=100 backend frontend`. Do not paste ticket URLs or JWTs in public logs.

## Deployment and limits

The WebSocket hub is **in-memory and supports one backend instance**. If you scale to multiple backend replicas, move broadcasts into a shared message bus (Redis, RabbitMQ, etc.) and adapt the ticket/session design. An ack lost after the server commits a message can result in an uncertain send status: the UI tells users to check history before retrying. If exactly-once client retries are required, add an idempotent message key with a DB unique constraint.

**Validation in this build environment:** source structure, TypeScript parse diagnostics, JSON parsing and ZIP CRC checks only. Maven, Angular template build, and live two-browser tests could not be run here; those checks must pass before public rollout.
