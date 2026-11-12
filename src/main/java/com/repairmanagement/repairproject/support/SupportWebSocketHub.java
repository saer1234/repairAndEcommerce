package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.CompanyMemberRepository;
import com.repairmanagement.repairproject.company.MemberRole;
import com.repairmanagement.repairproject.user.UserService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bidirectional native WebSocket transport. REST stays as the authenticated history
 * and fallback interface. All incoming text is persisted using SupportService,
 * preserving company access checks and the shop-online requirement.
 * Connections and broadcasts are local to this single JVM (one backend replica).
 */
@Component
public class SupportWebSocketHub extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, Connection> active = new ConcurrentHashMap<>();
    // Ensures an old socket cannot delete a newer socket's presence lease.
    private final ConcurrentHashMap<String, String> staffSessionOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> socketStaffKeys = new ConcurrentHashMap<>();
    private final SupportService service;
    private final SupportPresenceService presence;
    private final UserService users;
    private final CompanyMemberRepository memberships;
    private final JsonMapper json;
    private final SupportClientConnections clients;

    public SupportWebSocketHub(SupportService service, SupportPresenceService presence,
            UserService users, CompanyMemberRepository memberships,
            JsonMapper json, SupportClientConnections clients) {
        this.service = service;
        this.presence = presence;
        this.users = users;
        this.memberships = memberships;
        this.json = json;
        this.clients = clients;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var ticket = (SupportWebSocketTickets.Ticket) session.getAttributes().get("supportTicket");
        if (ticket == null || !ticket.jwtExpires().isAfter(Instant.now())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(16384);
        Connection connection = new Connection(session, ticket);
        active.put(session.getId(), connection);
        if (!allowed(connection)) {
            close(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (isClient(connection)) {
            clients.joined(ticket.companyId(), ticket.userId(), session.getId());
            clientPresenceChanged(ticket.companyId(), ticket.userId(), true);
            // Reopening happens only after a verified, active client socket exists.
            // It also handles a fresh page load and automatic WebSocket reconnect.
            try {
                if (service.reopenWhenClientOnline(ticket.companyId(), ticket.userId()))
                    changed(ticket.companyId(), ticket.userId());
            } catch (Exception ex) {
                // Do not leave an untracked socket if database recovery fails.
                close(session, CloseStatus.SERVER_ERROR);
                return;
            }
        }
        send(session, Map.of("type", "connected", "companyId", ticket.companyId()));
        send(session, availabilityEvent(ticket.companyId(), presence.onlineCount(ticket.companyId())));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) throws Exception {
        Connection connection = active.get(session.getId());
        if (connection == null || !allowed(connection)) {
            close(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        Incoming incoming;
        try {
            incoming = json.readValue(raw.getPayload(), Incoming.class);
        } catch (Exception ex) {
            sendError(session, null, "Invalid message format");
            return;
        }
        if (incoming != null && "staff_presence_bind".equals(incoming.type())) {
            if (!isStaff(connection) || incoming.sessionId() == null ||
                !incoming.sessionId().matches("[a-fA-F0-9-]{36}")) {
                close(session, CloseStatus.POLICY_VIOLATION);
                return;
            }
            String key = connection.ticket().companyId() + ":" + connection.ticket().userId() + ":" + incoming.sessionId();
            String previous = socketStaffKeys.put(session.getId(), key);
            if (previous != null && !previous.equals(key)) staffSessionOwners.remove(previous, session.getId());
            staffSessionOwners.put(key, session.getId());
            return;
        }
        if (incoming == null || !"send".equals(incoming.type())) {
            sendError(session, incoming == null ? null : incoming.requestId(), "Unsupported message type");
            return;
        }
        if (incoming.requestId() == null || incoming.requestId().isBlank() ||
                incoming.requestId().length() > 80 || incoming.conversationId() == null ||
                incoming.body() == null || incoming.body().isBlank() ||
                incoming.body().length() > 2000) {
            sendError(session, incoming.requestId(), "Message must be 1 to 2000 characters");
            return;
        }
        try {
            var message = service.send(connection.ticket().companyId(), incoming.conversationId(),
                new SupportSendRequest(incoming.body()), connection.ticket().email());
            var thread = service.conversationForEvent(connection.ticket().companyId(),
                incoming.conversationId(), connection.ticket().email());
            // Service transactions have committed before notifying connected clients.
            send(session, Map.of("type", "ack", "requestId", incoming.requestId(), "message", message));
            messageAdded(connection.ticket().companyId(), thread.customerId(), message);
        } catch (ResponseStatusException ex) {
            sendError(session, incoming.requestId(),
                ex.getStatusCode() == HttpStatus.CONFLICT ?
                "This shop is offline. Send an offline inquiry instead." :
                "Unable to send: you may no longer have access to this conversation.");
        } catch (Exception ex) {
            // Do not expose internal database errors or customer details to clients.
            sendError(session, incoming.requestId(), "Message could not be delivered");
        }
    }

    /** Immediately terminate all open sockets belonging to a removed/changed staff member. */
    public void revoke(Long companyId, Long userId) {
        for (Connection connection : active.values()) {
            if (companyId.equals(connection.ticket().companyId()) &&
                userId.equals(connection.ticket().userId()))
                close(connection.session(), CloseStatus.POLICY_VIOLATION);
        }
    }

    /** Notifies just the customer and current members of the specific shop. */
    public void messageAdded(Long companyId, Long customerId, SupportMessageView message) {
        broadcast(companyId, customerId, Map.of("type", "message", "companyId", companyId,
            "conversationId", message.conversationId(), "message", message));
    }

    /** Staff receive a real-time customer online/offline signal, independent of DB grace period. */
    public void clientPresenceChanged(Long companyId, Long customerId, boolean online) {
        Object event = Map.of("type", "customer_presence", "companyId", companyId,
            "customerId", customerId, "online", online);
        for (Connection connection : active.values()) {
            if (!companyId.equals(connection.ticket().companyId()) || !isStaff(connection)) continue;
            if (!allowed(connection)) {
                close(connection.session(), CloseStatus.POLICY_VIOLATION);
                continue;
            }
            try { send(connection.session(), event); }
            catch (IOException ex) { close(connection.session(), CloseStatus.SERVER_ERROR); }
        }
    }

    private Object availabilityEvent(Long companyId, long count) {
        return Map.of("type", "staff_availability", "companyId", companyId,
            "online", count > 0, "availableAgents", count);
    }

    /** Commit first, then deliver the new staff availability to all subscribed clients. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void availabilityChanged(SupportAvailabilityChanged event) {
        Object frame = availabilityEvent(event.companyId(), event.availableAgents());
        for (Connection connection : active.values()) {
            if (!event.companyId().equals(connection.ticket().companyId())) continue;
            if (!allowed(connection)) { close(connection.session(), CloseStatus.POLICY_VIOLATION); continue; }
            try { send(connection.session(), frame); }
            catch (IOException ex) { close(connection.session(), CloseStatus.SERVER_ERROR); }
        }
    }

    /** Used when an offline inquiry, conversation or status changes. */
    public void changed(Long companyId, Long customerId) {
        broadcast(companyId, customerId, Map.of("type", "refresh", "companyId", companyId));
    }

    private void broadcast(Long companyId, Long customerId, Object event) {
        for (Connection connection : active.values()) {
            if (!companyId.equals(connection.ticket().companyId())) continue;
            boolean intended = customerId.equals(connection.ticket().userId()) || isStaff(connection);
            if (!intended) {
                // Former staff must not retain a connection until the next heartbeat.
                if (!allowed(connection)) close(connection.session(), CloseStatus.POLICY_VIOLATION);
                continue;
            }
            if (!allowed(connection)) {
                close(connection.session(), CloseStatus.POLICY_VIOLATION);
                continue;
            }
            try {
                send(connection.session(), event);
            } catch (IOException ex) {
                close(connection.session(), CloseStatus.SERVER_ERROR);
            }
        }
    }

    private boolean isClient(Connection conn) {
        try {
            return users.currentUser(conn.ticket().email()).getPlatformRole() ==
                com.repairmanagement.repairproject.user.PlatformRole.CLIENT && !isStaff(conn);
        } catch (Exception ex) { return false; }
    }

    private boolean isStaff(Connection conn) {
        var t = conn.ticket();
        var membership = memberships.findByCompany_IdAndUser_IdAndActiveTrue(t.companyId(), t.userId());
        return membership.isPresent() &&
            (membership.get().getMemberRole() == MemberRole.OWNER ||
             membership.get().getMemberRole() == MemberRole.MANAGER ||
             membership.get().getMemberRole() == MemberRole.EMPLOYEE);
    }

    private boolean allowed(Connection connection) {
        var ticket = connection.ticket();
        if (!ticket.jwtExpires().isAfter(Instant.now()) || !connection.session().isOpen()) return false;
        try {
            // Re-check live DB roles so removed employees immediately stop receiving messages.
            var current = users.currentUser(ticket.email());
            if (!current.getId().equals(ticket.userId())) return false;
            presence.availability(ticket.companyId(), ticket.email());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void sendError(WebSocketSession session, String requestId, String reason) throws IOException {
        var message = new java.util.HashMap<String, Object>();
        message.put("type", "error");
        message.put("message", reason);
        if (requestId != null) message.put("requestId", requestId);
        send(session, message);
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        if (!session.isOpen()) return;
        String text = json.writeValueAsString(payload);
        // Spring's standard WebSocket sessions do not permit concurrent sends.
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(new TextMessage(text));
        }
    }

    @Scheduled(fixedDelay = 20000)
    public void keepAlive() {
        for (Connection connection : active.values()) {
            if (!allowed(connection)) {
                close(connection.session(), CloseStatus.POLICY_VIOLATION);
                continue;
            }
            try {
                synchronized (connection.session()) {
                    if (connection.session().isOpen())
                        connection.session().sendMessage(new PingMessage(ByteBuffer.wrap(new byte[0])));
                }
            } catch (Exception ex) {
                close(connection.session(), CloseStatus.SERVER_ERROR);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        forget(session);
    }

    private void forget(WebSocketSession session) {
        Connection removed = active.remove(session.getId());
        String staffKey = socketStaffKeys.remove(session.getId());
        if (removed != null && staffKey != null && staffSessionOwners.remove(staffKey, session.getId())) {
            String sessionId = staffKey.substring(staffKey.lastIndexOf(':') + 1);
            // A bound staff connection is the authoritative presence owner for this page.
            try { presence.leaveDisconnected(removed.ticket().companyId(), removed.ticket().userId(), sessionId); }
            catch (Exception ignored) { /* Heartbeat lease remains a fallback for database outages. */ }
        }
        if (removed != null && isClient(removed) &&
                clients.left(removed.ticket().companyId(), removed.ticket().userId(), session.getId())) {
            // Notify staff as soon as the LAST client tab leaves, not after the DB grace period.
            clientPresenceChanged(removed.ticket().companyId(), removed.ticket().userId(), false);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void closeOfflineCustomers() {
        for (var client : clients.expired()) {
            if (clients.isOnline(client.companyId(), client.customerId())) continue;
            try {
                if (service.closeWhenClientOffline(client.companyId(), client.customerId()))
                    changed(client.companyId(), client.customerId());
            } catch (Exception ignored) {
                // A transient database issue must not stop cleanup for other customers.
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session, CloseStatus.SERVER_ERROR);
    }

    private void close(WebSocketSession session, CloseStatus status) {
        forget(session);
        try { if (session.isOpen()) session.close(status); } catch (IOException ignored) { }
    }

    private record Connection(WebSocketSession session, SupportWebSocketTickets.Ticket ticket) {}
    public record Incoming(String type, String requestId, Long conversationId, String body, String sessionId) {}
}
