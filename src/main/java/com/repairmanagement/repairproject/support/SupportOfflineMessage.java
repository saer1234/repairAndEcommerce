package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "support_offline_messages")
public class SupportOfflineMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(name = "body", nullable = false, length = 2000)
    private String body;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "reply", length = 2000)
    private String reply;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by")
    private User repliedBy;
    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    protected SupportOfflineMessage() {}
    public SupportOfflineMessage(Company company, User customer, String body) {
        this.company = company;
        this.customer = customer;
        this.body = body;
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }
    public void reply(User staff, String text) {
        this.reply = text;
        this.repliedBy = staff;
        this.repliedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public User getCustomer() { return customer; }
    public String getBody() { return body; }
    public String getReply() { return reply; }
    public User getRepliedBy() { return repliedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRepliedAt() { return repliedAt; }
}
