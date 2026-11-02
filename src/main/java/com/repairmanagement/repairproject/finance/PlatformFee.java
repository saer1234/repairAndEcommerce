package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.order.CustomerOrder;
import com.repairmanagement.repairproject.repair.RepairRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="platform_fees")
public class PlatformFee {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false) private Company company;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private CustomerOrder order;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="repair_id") private RepairRequest repair;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="fee_type",nullable=false,length=25) private FeeType feeType;
    @Column(name="fee_amount",nullable=false,precision=12,scale=2) private BigDecimal feeAmount;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable=false,length=25) private FeeStatus status=FeeStatus.PENDING;
    @Column(name="earned_at") private LocalDateTime earnedAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    protected PlatformFee() {}
    public PlatformFee(Company company,CustomerOrder order,RepairRequest repair,FeeType feeType,BigDecimal amount){
       this.company=company;this.order=order;this.repair=repair;this.feeType=feeType;this.feeAmount=amount;
       this.currencyCode=company.getCurrencyCode();
    }
    @PrePersist void created(){createdAt=LocalDateTime.now(ZoneOffset.UTC);}
    public void earn(){status=FeeStatus.EARNED;earnedAt=LocalDateTime.now(ZoneOffset.UTC);}
    public Long getId(){return id;}
    public FeeType getFeeType(){return feeType;}
    public BigDecimal getFeeAmount(){return feeAmount;}
    public String getCurrencyCode(){return currencyCode;}
    public FeeStatus getStatus(){return status;}
    public LocalDateTime getEarnedAt(){return earnedAt;}
}
