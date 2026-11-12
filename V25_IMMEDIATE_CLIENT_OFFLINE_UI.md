# V25: Fast staff composer lock on client disconnect

## Behavior
- When the final authenticated client WebSocket disconnects, `SupportClientConnections.left` returns `true`.
- `SupportWebSocketHub.forget` sends an immediate scoped `customer_presence` WebSocket event (`online: false`) to authorized staff.
- `SupportPage.customerPresence` updates right away and the selected conversation composer disappears.
- A new authenticated client WebSocket emits `online: true`, restoring the composer when the conversation status is OPEN.
- Database `CLOSED` transition stays controlled by V24's 8-second reconnect grace plus 2-second scheduled scan; it preserves fast refreshes without flipping persisted status.
- A client with more than one open tab remains online until its last socket disconnects.

## Cautions
- No absolute timing guarantee when a browser crashes or the network silently drops; WebSocket timeout/heartbeat determines detection.
- This implementation uses in-memory WebSocket state and is appropriate for the existing single-JVM deployment. Multi-instance setups need shared presence/event bus.
- If the employee opens the support page after the client has already left, immediate presence isn't replayed, so the persisted CLOSED status (after grace) is the fallback. For exact initial presence during the grace window, implement an authenticated presence snapshot API or initial per-customer snapshot.
- Existing V24 server-side send validation remains authoritative; the UI is not a security boundary.
