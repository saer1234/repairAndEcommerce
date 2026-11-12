package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportService {
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final CompanyRepository companies;
    private final CompanyMemberRepository memberships;
    private final UserService users;
    private final SupportPresenceService presence;
    private final SupportClientConnections clients;

    public SupportService(SupportConversationRepository conversations, SupportMessageRepository messages,
                          CompanyRepository companies, CompanyMemberRepository memberships,
                          UserService users, SupportPresenceService presence, SupportClientConnections clients) {
        this.conversations = conversations;
        this.messages = messages;
        this.companies = companies;
        this.memberships = memberships;
        this.users = users;
        this.presence = presence;
        this.clients = clients;
    }

    private User customer(String email) {
        User user = users.currentUser(email);
        if (user.getPlatformRole() != PlatformRole.CLIENT ||
            memberships.existsByUser_IdAndActiveTrue(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only client accounts may open support chats");
        }
        return user;
    }

    private User staff(Long companyId, String email) {
        User user = users.currentUser(email);
        var membership = memberships.findByCompany_IdAndUser_IdAndActiveTrue(companyId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Company support access required"));
        if (membership.getMemberRole() != MemberRole.OWNER && membership.getMemberRole() != MemberRole.MANAGER
            && membership.getMemberRole() != MemberRole.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company support access required");
        }
        return user;
    }

    private SupportConversation accessible(Long companyId, Long conversationId, String email) {
        User user = users.currentUser(email);
        SupportConversation thread = conversations.findByIdAndCompany_Id(conversationId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (thread.getCustomer().getId().equals(user.getId())) {
            customer(email);
        } else {
            staff(companyId, email);
        }
        return thread;
    }

    @Transactional(readOnly = true)
    public SupportConversationView mine(Long companyId, String email) {
        User user = customer(email);
        return conversations.findByCompany_IdAndCustomer_Id(companyId, user.getId())
            .map(SupportConversationView::of).orElse(null);
    }

    @Transactional
    public SupportConversationView start(Long companyId, String email) {
        User user = customer(email);
        requireAgentOnline(companyId);
        requireClientOnline(companyId, user.getId());
        var existing = conversations.findByCompany_IdAndCustomer_Id(companyId, user.getId());
        if (existing.isPresent()) {
            // The customer is back and explicitly requested live chat.
            // Reuse the original conversation, never create a duplicate.
            var thread = existing.get();
            if (thread.getStatus() == SupportStatus.CLOSED) thread.changeStatus(SupportStatus.OPEN);
            return SupportConversationView.of(thread);
        }
        Company company = companies.findById(companyId)
            .filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        // Unique(company_id, customer_id) also guards against concurrent duplicate threads.
        return SupportConversationView.of(conversations.saveAndFlush(new SupportConversation(company, user)));
    }

    @Transactional(readOnly = true)
    public List<SupportConversationView> inbox(Long companyId, String email) {
        staff(companyId, email);
        return conversations.findByCompany_IdOrderByUpdatedAtDesc(companyId).stream()
            .map(SupportConversationView::of).toList();
    }

    @Transactional(readOnly = true)
    public SupportConversationView conversationForEvent(Long companyId, Long conversationId, String email) {
        return SupportConversationView.of(accessible(companyId, conversationId, email));
    }

    @Transactional(readOnly = true)
    public List<SupportMessageView> read(Long companyId, Long conversationId, String email) {
        SupportConversation thread = accessible(companyId, conversationId, email);
        // Return the latest 100 messages in chronological order, rather than loading the entire history.
        List<SupportMessage> latest = new ArrayList<>(messages.findByConversation_IdOrderByIdDesc(
            thread.getId(), PageRequest.of(0, 100)));
        Collections.reverse(latest);
        return latest.stream().map(m -> SupportMessageView.of(m, thread)).toList();
    }

    @Transactional
    public SupportMessageView send(Long companyId, Long conversationId, SupportSendRequest request, String email) {
        SupportConversation thread = accessible(companyId, conversationId, email);
        User sender = users.currentUser(email);
        // Keep history readable, but live messages require both sides online.
        requireAgentOnline(companyId);
        requireClientOnline(companyId, thread.getCustomer().getId());
        if (thread.getStatus() == SupportStatus.CLOSED && !sender.getId().equals(thread.getCustomer().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation is closed");
        String body = request.body().trim();
        if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is empty");
        // Sending a message reopens a closed conversation.
        thread.changeStatus(SupportStatus.OPEN);
        SupportMessage saved = messages.saveAndFlush(new SupportMessage(thread, sender, body));
        return SupportMessageView.of(saved, thread);
    }

    private void requireClientOnline(Long companyId, Long customerId) {
        if (!clients.isOnline(companyId, customerId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Customer is offline. The live chat has ended.");
    }

    /**
     * Called after an authenticated customer socket is registered. Reopen existing
     * history automatically when the customer returns to the support page.
     * Never creates a conversation and never permits staff to reopen an offline customer.
     * The WebSocket hub broadcasts the updated status after this transaction commits.
     */
    @Transactional
    public boolean reopenWhenClientOnline(Long companyId, Long customerId) {
        if (!clients.isOnline(companyId, customerId)) return false;
        var existing = conversations.findByCompany_IdAndCustomer_Id(companyId, customerId);
        if (existing.isEmpty() || existing.get().getStatus() != SupportStatus.CLOSED) return false;
        if (!clients.isOnline(companyId, customerId)) return false;
        existing.get().changeStatus(SupportStatus.OPEN);
        return true;
    }

    /** Close live chat after the customer's final WebSocket disconnects and the grace period passes. */
    @Transactional
    public boolean closeWhenClientOffline(Long companyId, Long customerId) {
        if (clients.isOnline(companyId, customerId)) return false;
        var existing = conversations.findByCompany_IdAndCustomer_Id(companyId, customerId);
        if (existing.isEmpty() || existing.get().getStatus() != SupportStatus.OPEN) return false;
        if (clients.isOnline(companyId, customerId)) return false;
        existing.get().changeStatus(SupportStatus.CLOSED);
        return true;
    }

    private void requireAgentOnline(Long companyId) {
        if (presence.onlineCount(companyId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This shop is offline. Please send an offline inquiry instead.");
        }
    }

    @Transactional
    public SupportConversationView setStatus(Long companyId, Long conversationId,
                                              SupportStatusRequest request, String email) {
        User actor = staff(companyId, email);
        var member = memberships.findByCompany_IdAndUser_IdAndActiveTrue(companyId, actor.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Company access required"));
        if (member.getMemberRole() == MemberRole.EMPLOYEE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an owner or manager may close a conversation");
        SupportConversation thread = conversations.findByIdAndCompany_Id(conversationId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (request.status() == SupportStatus.OPEN)
            requireClientOnline(companyId, thread.getCustomer().getId());
        thread.changeStatus(request.status());
        return SupportConversationView.of(thread);
    }
}
