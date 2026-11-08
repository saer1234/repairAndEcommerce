package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportOfflineService {
    private final SupportOfflineMessageRepository messages;
    private final CompanyRepository companies;
    private final CompanyMemberRepository memberships;
    private final UserService users;
    private final SupportPresenceService presence;

    public SupportOfflineService(SupportOfflineMessageRepository messages, CompanyRepository companies,
                                 CompanyMemberRepository memberships, UserService users,
                                 SupportPresenceService presence) {
        this.messages = messages;
        this.companies = companies;
        this.memberships = memberships;
        this.users = users;
        this.presence = presence;
    }

    private User client(String email) {
        User user = users.currentUser(email);
        if (user.getPlatformRole() != PlatformRole.CLIENT ||
            memberships.existsByUser_IdAndActiveTrue(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client account required");
        return user;
    }

    @Transactional
    public SupportOfflineMessageView submit(Long companyId, SupportSendRequest request, String email) {
        User user = client(email);
        Company company = companies.findById(companyId).filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not available"));
        String body = request.body().trim();
        if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is empty");
        return SupportOfflineMessageView.of(messages.saveAndFlush(new SupportOfflineMessage(company, user, body)));
    }

    @Transactional(readOnly = true)
    public List<SupportOfflineMessageView> mine(Long companyId, String email) {
        User user = client(email);
        return messages.findTop100ByCompany_IdAndCustomer_IdOrderByCreatedAtDesc(companyId, user.getId())
            .stream().map(SupportOfflineMessageView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportOfflineMessageView> inbox(Long companyId, String email) {
        presence.requireStaff(companyId, email);
        return messages.findTop100ByCompany_IdOrderByCreatedAtDesc(companyId)
            .stream().map(SupportOfflineMessageView::of).toList();
    }

    @Transactional
    public SupportOfflineMessageView reply(Long companyId, Long messageId, SupportOfflineReplyRequest request,
                                          String email) {
        presence.requireStaff(companyId, email);
        User staff = users.currentUser(email);
        SupportOfflineMessage message = messages.findByIdAndCompany_Id(messageId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offline message not found"));
        if (message.getReply() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This inquiry has already been answered");
        String body = request.body().trim();
        if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply is empty");
        message.reply(staff, body);
        return SupportOfflineMessageView.of(messages.saveAndFlush(message));
    }
}
