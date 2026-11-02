package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "shopping_carts")
public class ShoppingCart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ShoppingCart() {}
    public ShoppingCart(Company company, User customer) {
        this.company = company;
        this.customer = customer;
    }
    @PrePersist void beforeInsert() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }
    @PreUpdate void beforeUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public User getCustomer() { return customer; }
}
