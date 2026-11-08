package com.repairmanagement.repairproject.support;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/support")
public class SupportController {
    private final SupportService service;
    private final SupportPresenceService presence;
    private final SupportOfflineService offline;
    private final SupportEventStream eventStream;
    private final SupportWebSocketTickets tickets;

    // Presence and offline messages are enforced by their own service-level membership checks.
    public SupportController(SupportService service, SupportPresenceService presence, SupportOfflineService offline, SupportEventStream eventStream, SupportWebSocketTickets tickets) {
        this.service = service;
        this.presence = presence;
        this.offline = offline;
        this.eventStream = eventStream;
        this.tickets = tickets;
    }

    /** A bearer-authenticated request obtains a 30-second, single-use WebSocket ticket. */
    @PostMapping("/ws-ticket")
    public SupportWebSocketTickets.TicketView websocketTicket(
            @PathVariable Long companyId,
            org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
        return tickets.issue(companyId, principal);
    }

    @GetMapping("/availability")
    public SupportAvailabilityView availability(@PathVariable Long companyId, Principal principal) {
        return presence.availability(companyId, principal.getName());
    }

    @PostMapping("/presence/heartbeat")
    public SupportAvailabilityView heartbeat(@PathVariable Long companyId,
                                            @Valid @RequestBody SupportHeartbeatRequest request,
                                            Principal principal) {
        return presence.heartbeat(companyId, request, principal.getName());
    }

    @DeleteMapping("/presence/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long companyId, @PathVariable String sessionId, Principal principal) {
        presence.leave(companyId, sessionId, principal.getName());
    }

    @GetMapping("/offline-messages/mine")
    public List<SupportOfflineMessageView> myOfflineMessages(@PathVariable Long companyId, Principal principal) {
        return offline.mine(companyId, principal.getName());
    }

    @PostMapping("/offline-messages")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportOfflineMessageView submitOffline(@PathVariable Long companyId,
                                                    @Valid @RequestBody SupportSendRequest request,
                                                    Principal principal) {
        var result = offline.submit(companyId, request, principal.getName());
        eventStream.changed(companyId, result.customerId());
        return result;
    }

    @GetMapping("/offline-messages")
    public List<SupportOfflineMessageView> offlineInbox(@PathVariable Long companyId, Principal principal) {
        return offline.inbox(companyId, principal.getName());
    }

    @PatchMapping("/offline-messages/{messageId}/reply")
    public SupportOfflineMessageView replyOffline(@PathVariable Long companyId, @PathVariable Long messageId,
                                                   @Valid @RequestBody SupportOfflineReplyRequest request,
                                                   Principal principal) {
        var result = offline.reply(companyId, messageId, request, principal.getName());
        eventStream.changed(companyId, result.customerId());
        return result;
    }

    @GetMapping("/mine")
    public SupportConversationView mine(@PathVariable Long companyId, Principal principal) {
        return service.mine(companyId, principal.getName());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportConversationView start(@PathVariable Long companyId, Principal principal) {
        var result = service.start(companyId, principal.getName());
        eventStream.changed(companyId, result.customerId());
        return result;
    }

    @GetMapping("/conversations")
    public List<SupportConversationView> inbox(@PathVariable Long companyId, Principal principal) {
        return service.inbox(companyId, principal.getName());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<SupportMessageView> read(@PathVariable Long companyId, @PathVariable Long conversationId,
                                         Principal principal) {
        return service.read(companyId, conversationId, principal.getName());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportMessageView send(@PathVariable Long companyId, @PathVariable Long conversationId,
                                   @Valid @RequestBody SupportSendRequest body, Principal principal) {
        var result = service.send(companyId, conversationId, body, principal.getName());
        var conversation = service.conversationForEvent(companyId, conversationId, principal.getName());
        eventStream.message(companyId, conversation.customerId(), result);
        return result;
    }

    @PatchMapping("/conversations/{conversationId}/status")
    public SupportConversationView status(@PathVariable Long companyId, @PathVariable Long conversationId,
                                          @Valid @RequestBody SupportStatusRequest body, Principal principal) {
        var result = service.setStatus(companyId, conversationId, body, principal.getName());
        eventStream.changed(companyId, result.customerId());
        return result;
    }
}
