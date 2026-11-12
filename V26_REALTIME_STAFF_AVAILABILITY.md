# V26 — Real-time staff availability and support-page leaving

Base: V25 full project. No schema migration necessary.

## Behavior

- Staff click **I am available for chat**: after the heartbeat transaction commits, clients and staff on the same company support socket get `staff_availability` with the current number of *distinct* active agents. The client UI is updated from that event, without waiting for the 30-second polling fallback.
- Staff click **Go offline**: after the delete transaction commits, the same event is pushed. If another staff member is still available the client support UI remains online.
- Staff leave the support page (normal navigation, tab close): the client-side native socket closes. Its server-side WebSocket session has been bound to the same browser's staff presence session. When the last owning socket disconnects, its presence session is removed and availability is broadcast immediately after commit. The HTTP DELETE remains a best-effort fallback.
- Staff connection drops and reconnects: a fresh WebSocket connection binds to the same session and renews its heartbeat immediately on reconnect. Stale socket close callbacks cannot delete a newer socket's bound session.
- On connection establishment the backend sends the latest availability snapshot so rejoining clients needn't wait for an availability change event.

## Protocol

Client -> server (only authorized active staff):

```json
{"type":"staff_presence_bind", "sessionId":"<UUID of page's presence session>"}
```

Server -> client/staff (same shop):

```json
{"type":"staff_availability", "companyId":42, "online":true, "availableAgents":1}
```

## Limitations and production checks

- An abrupt network drop may delay `afterConnectionClosed`; lower the 20-second keepAlive interval or add application heartbeat timeouts if faster detection is necessary. For normal page exit, native WebSocket close should be prompt.
- Current WebSocket and presence event delivery is **single JVM**. If you run multiple backend replicas, use a shared pub/sub mechanism (e.g. Redis) to publish events across replicas, and distributed ownership for WebSocket presence.
- Run `mvn test`, `mvn package`, and `npm ci && npm run build` in a configured development environment, then perform two-browser checks for on/off/navigation and reconnect, including two staff in one company. These builds have not been executed in this environment.
