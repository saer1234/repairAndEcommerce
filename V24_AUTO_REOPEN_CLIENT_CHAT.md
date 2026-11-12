# V24 – Automatically reopen the customer chat on page visit

This update extends V22/V23.

- As soon as a customer opens the support page and their authenticated WebSocket connects, any **existing CLOSED conversation** for that company/customer is reopened automatically. No button press or message is required.
- The backend broadcasts a refresh to the company inbox; agents see `OPEN` without reloading.
- The Angular customer page reloads `/mine` when WebSocket reconnects or receives a refresh, so a previously CLOSED badge and disabled composer update automatically.
- Returning clients reuse their existing conversation and saved message history; no duplicate conversations are created.
- When the client leaves again, V23's 8-second grace period and automatic close still apply. Staff can't message offline clients.
- A newly visiting customer with no history does **not** get an empty conversation automatically; normal Start Chat remains available when staff are online.
- Opening a conversation does **not** bypass the existing staff-presence rule for sending live messages.

**Files modified:** `SupportService.java`, `SupportWebSocketHub.java`, `support.page.ts`, `SupportServiceTest.java`.

**Operational note:** WebSocket presence is local to one backend replica. To scale to multiple backend instances, use distributed presence and change events.
