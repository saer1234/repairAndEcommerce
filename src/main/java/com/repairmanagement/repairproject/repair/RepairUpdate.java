package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "repair_updates")
public class RepairUpdate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_id", nullable = false)
    private RepairRequest repair;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_user_id", nullable = false)
    private User updatedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "new_status", nullable = false, length = 30)
    private RepairStatus newStatus;

    @Column(columnDefinition = "TEXT")
    private String note; // company-private note

    @Column(name = "customer_note", length = 2000)
    private String customerNote; // explicitly published by an authorized staff member

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RepairUpdate() {}
    public RepairUpdate(RepairRequest repair, User updatedBy, RepairStatus newStatus, String note) {
        this.repair = repair;
        this.updatedBy = updatedBy;
        this.newStatus = newStatus;
        this.note = note;
    }
    public RepairUpdate(RepairRequest repair, User updatedBy, RepairStatus newStatus,
                        String note, String customerNote) {
        this(repair, updatedBy, newStatus, note);
        this.customerNote = customerNote;
    }

    @PrePersist
    void created() { this.createdAt = LocalDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public User getUpdatedBy() { return updatedBy; }
    public RepairStatus getNewStatus() { return newStatus; }
    public String getNote() { return note; }
    public String getCustomerNote() { return customerNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
