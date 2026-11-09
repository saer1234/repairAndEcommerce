package com.repairmanagement.repairproject.company;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "email", length = 190)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "whish_wallet_phone", length = 40)
    private String whishWalletPhone;

    @Column(name = "delivery_enabled", nullable = false)
    private boolean deliveryEnabled = true;
    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal deliveryFee = java.math.BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 25)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Company() {}

    public Company(String name, String email, String phone, String address, String currencyCode) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.currencyCode = currencyCode;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getCurrencyCode() { return currencyCode; }
    public String getWhishWalletPhone() { return whishWalletPhone; }
    public void setWhishWalletPhone(String phone) { this.whishWalletPhone = phone; }
    public boolean isDeliveryEnabled() { return deliveryEnabled; }
    public java.math.BigDecimal getDeliveryFee() { return deliveryFee; }
    public void configureDelivery(boolean enabled, java.math.BigDecimal fee) {
        this.deliveryEnabled = enabled;
        this.deliveryFee = fee;
    }
    public CompanyStatus getStatus() { return status; }
}
