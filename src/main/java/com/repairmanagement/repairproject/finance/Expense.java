package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
@Entity @Table(name="expenses")
public class Expense {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false,length=80) private String category;
    @Column(length=255) private String description;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="expense_date",nullable=false) private LocalDate expenseDate;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_user_id") private User creator;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    protected Expense() {}
    public Expense(Company company,String category,String description,BigDecimal amount,String currencyCode,
                   LocalDate expenseDate,User creator){
       this.company=company;this.category=category;this.description=description;this.amount=amount;
       this.currencyCode=currencyCode;this.expenseDate=expenseDate;this.creator=creator;
    }
    @PrePersist void created(){createdAt=LocalDateTime.now(ZoneOffset.UTC);}
    public Long getId(){return id;}
    public String getCategory(){return category;}
    public String getDescription(){return description;}
    public BigDecimal getAmount(){return amount;}
    public String getCurrencyCode(){return currencyCode;}
    public LocalDate getExpenseDate(){return expenseDate;}
}
