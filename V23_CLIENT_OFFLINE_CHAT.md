# V23: Close company-side live chat when the customer disconnects

- Tracks authenticated customer WebSocket sessions per company and customer (all tabs count).
- After the last customer connection closes, an 8-second reconnect grace period begins.
- If the customer does not reconnect, the server marks their OPEN support conversation CLOSED and broadcasts a refresh to company staff.
- Staff cannot send new messages or reopen a conversation when the customer is offline. Existing chat history and offline inquiries are preserved.
- The Angular composer disappears for closed conversations, shows an end-of-chat message, and requires a real-time connection to start/send.
- No database schema changes required.

**Deployment note:** This uses JVM-local WebSocket presence, consistent with the repository's existing single-backend-instance WebSocket hub. If you deploy multiple backend replicas, move shared socket presence/closure notifications to Redis or another shared store before relying on this implementation.

**Behavior:** If a client has two browser tabs open, the chat stays active until both close. A brief reconnect does not end the chat. The staff inbox retains CLOSED conversations as read-only history rather than deleting them.
