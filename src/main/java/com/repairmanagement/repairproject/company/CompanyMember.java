package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "company_members")
public class CompanyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "member_role", nullable = false, length = 25)
    private MemberRole memberRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    protected CompanyMember() {}

    public CompanyMember(Company company, User user, MemberRole memberRole) {
        this.company = company;
        this.user = user;
        this.memberRole = memberRole;
    }

    @PrePersist
    void onCreate() { joinedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public User getUser() { return user; }
    public MemberRole getMemberRole() { return memberRole; }
    public boolean isActive() { return active; }
    public void changeRole(MemberRole role) { this.memberRole = role; }
    public void deactivate() { this.active = false; }
    public void reactivate(MemberRole role) { this.memberRole = role; this.active = true; }
}
