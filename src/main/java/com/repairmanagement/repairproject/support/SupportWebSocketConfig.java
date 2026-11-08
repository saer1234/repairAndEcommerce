package com.repairmanagement.repairproject.support;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

@Configuration
@EnableWebSocket
public class SupportWebSocketConfig implements WebSocketConfigurer {
    private final SupportWebSocketHub hub;
    private final SupportWebSocketTickets tickets;
    private final String origins;

    public SupportWebSocketConfig(SupportWebSocketHub hub, SupportWebSocketTickets tickets,
        @Value("${app.support.websocket.allowed-origins:http://localhost:4200,https://wolftech.space}") String origins) {
        this.hub = hub;
        this.tickets = tickets;
        this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowed = Arrays.stream(origins.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        registry.addHandler(hub, "/api/support/ws")
            .addInterceptors(new TicketHandshake(tickets))
            .setAllowedOriginPatterns(allowed);
    }

    private static final class TicketHandshake implements HandshakeInterceptor {
        private final SupportWebSocketTickets tickets;
        TicketHandshake(SupportWebSocketTickets tickets) { this.tickets = tickets; }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Map<String, Object> attributes) {
            String raw = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("ticket");
            var ticket = tickets.consume(raw);
            if (ticket == null) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("supportTicket", ticket);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Exception exception) { }
    }
}
