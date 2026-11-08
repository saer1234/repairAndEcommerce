package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_agent_presence", uniqueConstraints = @UniqueConstraint(
    name = "uq_support_agent_session", columnNames = {"company_id", "user_id", "session_id"}))
public class SupportAgentPresence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected SupportAgentPresence() {}
    public SupportAgentPresence(Company company, User user, String sessionId, LocalDateTime expiresAt) {
        this.company = company;
        this.user = user;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }
    public void renew(LocalDateTime expiry) { this.expiresAt = expiry; }
}
