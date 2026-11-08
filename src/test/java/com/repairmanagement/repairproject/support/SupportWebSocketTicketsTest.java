package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.user.PlatformRole;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportWebSocketTicketsTest {
    SupportPresenceService presence = mock(SupportPresenceService.class);
    UserService users = mock(UserService.class);
    SupportWebSocketTickets tickets = new SupportWebSocketTickets(presence, users);

    private JwtAuthenticationToken auth(long uid) {
        Jwt jwt = Jwt.withTokenValue("test-credential")
            .header("alg", "HS256")
            .subject("client@example.test")
            .claim("uid", uid)
            .issuedAt(Instant.now().minusSeconds(5))
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void issuedTicketIsSingleUseAndCompanyBound() {
        User client = new User("Client", "client@example.test", "hash", null, PlatformRole.CLIENT);
        ReflectionTestUtils.setField(client, "id", 44L);
        when(users.currentUser("client@example.test")).thenReturn(client);

        var issued = tickets.issue(5L, auth(44L));
        assertNotNull(issued.ticket());
        var authenticated = tickets.consume(issued.ticket());
        assertNotNull(authenticated);
        assertEquals(5L, authenticated.companyId());
        assertEquals(44L, authenticated.userId());
        assertNull(tickets.consume(issued.ticket()));
        verify(presence, atLeastOnce()).availability(5L, "client@example.test");
    }

    @Test
    void noTicketWhenJwtUserIdDoesNotMatchDatabaseUser() {
        User client = new User("Client", "client@example.test", "hash", null, PlatformRole.CLIENT);
        ReflectionTestUtils.setField(client, "id", 44L);
        when(users.currentUser("client@example.test")).thenReturn(client);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> tickets.issue(5L, auth(66L)));
    }

    @Test
    void unknownTicketCannotOpenConnection() {
        assertNull(tickets.consume("unknown"));
    }
}
