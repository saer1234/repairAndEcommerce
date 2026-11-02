package com.repairmanagement.repairproject.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="invoice_lines")
public class InvoiceLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id",nullable=false) private Invoice invoice;
    @Column(nullable=false,length=255) private String description;
    @Column(nullable=false,precision=10,scale=2) private BigDecimal quantity;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
    protected InvoiceLine() {}
    public InvoiceLine(Invoice invoice,String description,BigDecimal quantity,BigDecimal unitPrice){
       this.invoice=invoice;this.description=description;this.quantity=quantity;this.unitPrice=unitPrice;
    }
    public Long getId(){return id;}
    public String getDescription(){return description;}
    public BigDecimal getQuantity(){return quantity;}
    public BigDecimal getUnitPrice(){return unitPrice;}
}
