package com.repairmanagement.repairproject.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="payments")
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id",nullable=false) private Invoice invoice;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="payment_method",nullable=false,length=30) private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable=false,length=25) private PaymentStatus status=PaymentStatus.COMPLETED;
    @Column(name="provider_reference",length=190) private String providerReference;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    protected Payment() {}
    public Payment(Invoice invoice,BigDecimal amount,PaymentMethod method,String reference){
       this.invoice=invoice;this.amount=amount;this.paymentMethod=method;this.providerReference=reference;
    }
    @PrePersist void created(){createdAt=LocalDateTime.now(ZoneOffset.UTC);paidAt=createdAt;}
    public Long getId(){return id;}
    public BigDecimal getAmount(){return amount;}
    public PaymentMethod getPaymentMethod(){return paymentMethod;}
    public PaymentStatus getStatus(){return status;}
    public String getProviderReference(){return providerReference;}
    public LocalDateTime getPaidAt(){return paidAt;}
}
