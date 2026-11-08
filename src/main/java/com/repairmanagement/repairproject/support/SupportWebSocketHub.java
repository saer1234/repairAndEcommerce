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
    private final SupportService service;
    private final SupportPresenceService presence;
    private final UserService users;
    private final CompanyMemberRepository memberships;
    private final JsonMapper json;

    public SupportWebSocketHub(SupportService service, SupportPresenceService presence,
            UserService users, CompanyMemberRepository memberships,
            JsonMapper json) {
        this.service = service;
        this.presence = presence;
        this.users = users;
        this.memberships = memberships;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var ticket = (SupportWebSocketTickets.Ticket) session.getAttributes().get("supportTicket");
        if (ticket == null || !ticket.jwtExpires().isAfter(Instant.now())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(16384);
        active.put(session.getId(), new Connection(session, ticket));
        send(session, Map.of("type", "connected", "companyId", ticket.companyId()));
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

    /** Notifies just the customer and current members of the specific shop. */
    public void messageAdded(Long companyId, Long customerId, SupportMessageView message) {
        broadcast(companyId, customerId, Map.of("type", "message", "companyId", companyId,
            "conversationId", message.conversationId(), "message", message));
    }

    /** Used when an offline inquiry, conversation or status changes. */
    public void changed(Long companyId, Long customerId) {
        broadcast(companyId, customerId, Map.of("type", "refresh", "companyId", companyId));
    }

    private void broadcast(Long companyId, Long customerId, Object event) {
        for (Connection connection : active.values()) {
            if (!companyId.equals(connection.ticket().companyId())) continue;
            boolean intended = customerId.equals(connection.ticket().userId()) || isStaff(connection);
            if (!intended) continue;
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
        active.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session, CloseStatus.SERVER_ERROR);
    }

    private void close(WebSocketSession session, CloseStatus status) {
        active.remove(session.getId());
        try { if (session.isOpen()) session.close(status); } catch (IOException ignored) { }
    }

    private record Connection(WebSocketSession session, SupportWebSocketTickets.Ticket ticket) {}
    public record Incoming(String type, String requestId, Long conversationId, String body) {}
}
