package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportPresenceService {
    private static final int PRESENCE_SECONDS = 75;
    private static final List<MemberRole> SUPPORT_ROLES =
        List.of(MemberRole.OWNER, MemberRole.MANAGER, MemberRole.EMPLOYEE);
    private final SupportAgentPresenceRepository presence;
    private final CompanyMemberRepository memberships;
    private final CompanyRepository companies;
    private final UserService users;

    public SupportPresenceService(SupportAgentPresenceRepository presence,
                                  CompanyMemberRepository memberships, CompanyRepository companies,
                                  UserService users) {
        this.presence = presence;
        this.memberships = memberships;
        this.companies = companies;
        this.users = users;
    }

    private User staff(Long companyId, String email) {
        User user = users.currentUser(email);
        var membership = memberships.findByCompany_IdAndUser_IdAndActiveTrue(companyId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active shop membership required"));
        if (!SUPPORT_ROLES.contains(membership.getMemberRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support staff access required");
        return user;
    }

    public void requireStaff(Long companyId, String email) { staff(companyId, email); }

    @Transactional(readOnly = true)
    public long onlineCount(Long companyId) {
        return presence.countAvailableAgents(companyId, LocalDateTime.now(ZoneOffset.UTC), SUPPORT_ROLES);
    }

    @Transactional(readOnly = true)
    public SupportAvailabilityView availability(Long companyId, String email) {
        User user = users.currentUser(email);
        if (user.getPlatformRole() == PlatformRole.CLIENT &&
            !memberships.existsByUser_IdAndActiveTrue(user.getId())) {
            // Only active shops may receive live requests.
            companies.findById(companyId).filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not available"));
        } else {
            staff(companyId, email);
        }
        long count = onlineCount(companyId);
        return new SupportAvailabilityView(count > 0, count);
    }

    @Transactional
    public SupportAvailabilityView heartbeat(Long companyId, SupportHeartbeatRequest request, String email) {
        User user = staff(companyId, email);
        Company shop = companies.findById(companyId).filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not available"));
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(PRESENCE_SECONDS);
        SupportAgentPresence record = presence.findByCompany_IdAndUser_IdAndSessionId(
            companyId, user.getId(), request.sessionId()).orElseGet(() ->
                new SupportAgentPresence(shop, user, request.sessionId(), expiresAt));
        record.renew(expiresAt);
        presence.saveAndFlush(record);
        return new SupportAvailabilityView(true, onlineCount(companyId));
    }

    @Transactional
    public void leave(Long companyId, String sessionId, String email) {
        User user = staff(companyId, email);
        presence.deleteSession(companyId, user.getId(), sessionId);
    }

    // Cleanup of abandoned browser sessions; expired rows never count as online even before cleanup.
    @Scheduled(fixedDelayString = "${app.support.presence-cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpired() {
        presence.deleteExpired(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
    }
}
