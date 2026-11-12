package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.user.UserService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tickets are bound to one authenticated user and company, and are consumed at upgrade.
 * Unlike a JWT, a ticket cannot be reused if a query URL is logged by a reverse proxy.
 */
@Service
public class SupportWebSocketTickets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long LIFE_SECONDS = 30;
    private final ConcurrentHashMap<String, Ticket> pending = new ConcurrentHashMap<>();
    private final SupportPresenceService presence;
    private final UserService users;

    public SupportWebSocketTickets(SupportPresenceService presence, UserService users) {
        this.presence = presence;
        this.users = users;
    }

    public TicketView issue(Long companyId, JwtAuthenticationToken auth) {
        var user = users.currentUser(auth.getName());
        Object jwtUserId = auth.getToken().getClaim("uid");
        if (!(jwtUserId instanceof Number n) || n.longValue() != user.getId())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity");
        // Revalidate active staff membership or eligible client access on each issuance.
        presence.availability(companyId, auth.getName());
        Instant jwtExpiry = auth.getToken().getExpiresAt();
        if (jwtExpiry == null || !jwtExpiry.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        if (pending.size() >= 10000)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many pending connections");
        Instant expires = Instant.now().plusSeconds(LIFE_SECONDS);
        if (jwtExpiry.isBefore(expires)) expires = jwtExpiry;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(value, new Ticket(companyId, user.getId(), user.getEmail(), jwtExpiry, expires));
        return new TicketView(value, expires);
    }

    /** Atomically invalidates the credential, whether validation succeeds or not. */
    Ticket consume(String token) {
        if (token == null || token.length() > 128) return null;
        Ticket ticket = pending.remove(token);
        if (ticket == null || !ticket.ticketExpires().isAfter(Instant.now()) ||
                !ticket.jwtExpires().isAfter(Instant.now())) return null;
        try {
            var user = users.currentUser(ticket.email());
            if (!user.getId().equals(ticket.userId())) return null;
            presence.availability(ticket.companyId(), ticket.email());
            return ticket;
        } catch (Exception ex) {
            return null;
        }
    }

    public void revoke(Long companyId, Long userId) {
        pending.entrySet().removeIf(e -> companyId.equals(e.getValue().companyId()) &&
            userId.equals(e.getValue().userId()));
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanUp() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(e -> !e.getValue().ticketExpires().isAfter(now));
    }

    public record TicketView(String ticket, Instant expiresAt) {}
    record Ticket(Long companyId, Long userId, String email, Instant jwtExpires, Instant ticketExpires) {}
}
