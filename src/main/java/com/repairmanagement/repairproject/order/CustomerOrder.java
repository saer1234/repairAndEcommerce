package com.repairmanagement.repairproject.order;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(name = "order_number", nullable = false, length = 60)
    private String orderNumber;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 25)
    private OrderStatus status = OrderStatus.CONFIRMED;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name="customer_name_snapshot",length=150) private String customerNameSnapshot;
    @Column(name="customer_email_snapshot",length=190) private String customerEmailSnapshot;
    @Column(name="delivery_phone",length=40) private String deliveryPhone;
    @Column(name="delivery_street",length=255) private String deliveryStreet;
    @Column(name="delivery_city",length=120) private String deliveryCity;
    @Column(name="delivery_postal_code",length=24) private String deliveryPostalCode;
    @Column(name="delivery_country",length=100) private String deliveryCountry;
    @Column(name="delivery_notes",length=500) private String deliveryNotes;
    @Column(name="delivery_latitude",precision=10,scale=7) private BigDecimal deliveryLatitude;
    @Column(name="delivery_longitude",precision=10,scale=7) private BigDecimal deliveryLongitude;

    protected CustomerOrder() {}
    public CustomerOrder(Company company, User customer, String orderNumber, BigDecimal totalAmount) {
        this.company = company;
        this.customer = customer;
        this.customerNameSnapshot = customer.getFullName();
        this.customerEmailSnapshot = customer.getEmail();
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
    }
    public void setDelivery(CheckoutRequest delivery) {
        this.deliveryPhone = delivery.deliveryPhone().trim();
        this.deliveryStreet = delivery.streetAddress().trim();
        this.deliveryCity = delivery.city().trim();
        this.deliveryPostalCode = delivery.postalCode() == null ? null : delivery.postalCode().trim();
        this.deliveryCountry = delivery.country().trim();
        this.deliveryNotes = delivery.deliveryNotes() == null ? null : delivery.deliveryNotes().trim();
        this.deliveryLatitude = delivery.latitude();
        this.deliveryLongitude = delivery.longitude();
    }
    @PrePersist void beforeInsert() { orderedAt = LocalDateTime.now(ZoneOffset.UTC); }
    public void complete() {
        status = OrderStatus.COMPLETED;
        completedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public void cancel() { status = OrderStatus.CANCELLED; }
    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public User getCustomer() { return customer; }
    public String getOrderNumber() { return orderNumber; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot == null ? customer.getFullName() : customerNameSnapshot; }
    public String getCustomerEmailSnapshot() { return customerEmailSnapshot == null ? customer.getEmail() : customerEmailSnapshot; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public String getDeliveryStreet() { return deliveryStreet; }
    public String getDeliveryCity() { return deliveryCity; }
    public String getDeliveryPostalCode() { return deliveryPostalCode; }
    public String getDeliveryCountry() { return deliveryCountry; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public BigDecimal getDeliveryLatitude() { return deliveryLatitude; }
    public BigDecimal getDeliveryLongitude() { return deliveryLongitude; }
}
