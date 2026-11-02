package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "repair_requests")
public class RepairRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private User assignedEmployee;

    // Stored snapshot: the person to contact for this repair may not be the
    // account holder, and changing their profile must not change old repairs.
    // Nullable for rows created before migration V4.
    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "client_phone", length = 40)
    private String clientPhone;

    @Column(name = "device_type", nullable = false, length = 100)
    private String deviceType;

    @Column(length = 100)
    private String brand;

    @Column(length = 120)
    private String model;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private RepairStatus status = RepairStatus.NEW;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "work_performed", columnDefinition = "TEXT")
    private String workPerformed;

    @Column(name = "labor_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCharge = BigDecimal.ZERO;

    @Column(name = "labor_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected RepairRequest() {}

    public RepairRequest(Company company, User customer, String deviceType,
                         String brand, String model, String issueDescription) {
        this(company, customer, customer.getFullName(), customer.getPhone(),
            deviceType, brand, model, issueDescription);
    }

    public RepairRequest(Company company, User customer, String clientName,
                         String clientPhone, String deviceType, String brand,
                         String model, String issueDescription) {
        this.company = company;
        this.customer = customer;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.deviceType = deviceType;
        this.brand = brand;
        this.model = model;
        this.issueDescription = issueDescription;
    }

    @PrePersist
    void created() { this.createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    public void assign(User employee) { this.assignedEmployee = employee; }
    public void recordReport(String diagnosis, String workPerformed) {
        this.diagnosis = diagnosis.trim();
        this.workPerformed = workPerformed.trim();
    }
    public String getDiagnosis() { return diagnosis; }
    public String getWorkPerformed() { return workPerformed; }
    public void setLabor(BigDecimal charge, BigDecimal cost) {
        this.laborCharge = charge;
        this.laborCost = cost;
    }
    public void changeStatus(RepairStatus newStatus) {
        this.status = newStatus;
        if (newStatus == RepairStatus.COMPLETED) this.completedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public User getCustomer() { return customer; }
    public User getAssignedEmployee() { return assignedEmployee; }
    public String getClientName() { return clientName; }
    public String getClientPhone() { return clientPhone; }
    public String getDeviceType() { return deviceType; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getIssueDescription() { return issueDescription; }
    public RepairStatus getStatus() { return status; }
    public BigDecimal getLaborCharge() { return laborCharge; }
    public BigDecimal getLaborCost() { return laborCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
