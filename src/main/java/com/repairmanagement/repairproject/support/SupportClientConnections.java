package com.repairmanagement.repairproject.support;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Live customer presence for the single-instance native WebSocket deployment.
 * Presence is company-scoped and includes all browser tabs; a short grace period
 * avoids closing a conversation during normal reconnects or page reloads.
 */
@Component
public class SupportClientConnections {
    public record Client(Long companyId, Long customerId) {}
    private final Map<Client, Set<String>> sessions = new HashMap<>();
    private final Map<Client, Instant> disconnectedAt = new HashMap<>();
    private static final Duration RECONNECT_GRACE = Duration.ofSeconds(8);

    public synchronized void joined(Long companyId, Long customerId, String socketId) {
        Client client = new Client(companyId, customerId);
        sessions.computeIfAbsent(client, unused -> new HashSet<>()).add(socketId);
        disconnectedAt.remove(client);
    }

    public synchronized boolean left(Long companyId, Long customerId, String socketId) {
        Client client = new Client(companyId, customerId);
        Set<String> sockets = sessions.get(client);
        if (sockets == null) return false;
        sockets.remove(socketId);
        if (sockets.isEmpty()) {
            sessions.remove(client);
            disconnectedAt.putIfAbsent(client, Instant.now());
            return true;
        }
        return false;
    }

    public synchronized boolean isOnline(Long companyId, Long customerId) {
        return sessions.containsKey(new Client(companyId, customerId));
    }

    /** Claim overdue disconnections; a new connection cancels the pending closure. */
    public synchronized Set<Client> expired() {
        Instant deadline = Instant.now().minus(RECONNECT_GRACE);
        Set<Client> due = new HashSet<>();
        disconnectedAt.entrySet().removeIf(entry -> {
            if (entry.getValue().isAfter(deadline) || sessions.containsKey(entry.getKey())) return false;
            due.add(entry.getKey());
            return true;
        });
        return due;
    }
}
