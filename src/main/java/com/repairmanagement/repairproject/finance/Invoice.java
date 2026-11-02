package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.order.CustomerOrder;
import com.repairmanagement.repairproject.repair.RepairRequest;
import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="invoices")
public class Invoice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false) private Company company;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private User customer;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private CustomerOrder order;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="repair_id") private RepairRequest repair;
    @Column(name="invoice_number",nullable=false,length=60) private String invoiceNumber;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable=false,length=25) private InvoiceStatus status=InvoiceStatus.DRAFT;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal subtotal;
    @Column(name="tax_amount",nullable=false,precision=12,scale=2) private BigDecimal taxAmount;
    @Column(name="total_amount",nullable=false,precision=12,scale=2) private BigDecimal totalAmount;
    @Column(name="repair_technician_snapshot", length=150) private String repairTechnicianSnapshot;
    @Column(name="repair_diagnosis_snapshot", columnDefinition="TEXT") private String repairDiagnosisSnapshot;
    @Column(name="repair_work_snapshot", columnDefinition="TEXT") private String repairWorkSnapshot;
    @Column(name="repair_device_snapshot", length=360) private String repairDeviceSnapshot;
    @Column(name="repair_labor_cost_snapshot", precision=12, scale=2) private BigDecimal repairLaborCostSnapshot;
    @Column(name="repair_parts_cost_snapshot", precision=12, scale=2) private BigDecimal repairPartsCostSnapshot;
    @Column(name="issued_at") private LocalDateTime issuedAt;
    @Column(name="due_at") private LocalDateTime dueAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    protected Invoice() {}
    public Invoice(Company company,User customer,CustomerOrder order,RepairRequest repair,String invoiceNumber,
                   BigDecimal subtotal,BigDecimal taxAmount) {
        this.company=company;this.customer=customer;this.order=order;this.repair=repair;this.invoiceNumber=invoiceNumber;
        this.subtotal=subtotal;this.taxAmount=taxAmount;this.totalAmount=subtotal.add(taxAmount);
        if (repair != null) {
            this.repairTechnicianSnapshot = repair.getAssignedEmployee() == null ? null : repair.getAssignedEmployee().getFullName();
            this.repairDiagnosisSnapshot = repair.getDiagnosis();
            this.repairWorkSnapshot = repair.getWorkPerformed();
            this.repairDeviceSnapshot = String.join(" ", repair.getDeviceType(),
                repair.getBrand() == null ? "" : repair.getBrand(),
                repair.getModel() == null ? "" : repair.getModel()).trim();
            this.repairLaborCostSnapshot = repair.getLaborCost();
        }
    }
    @PrePersist void onCreate(){createdAt=LocalDateTime.now(ZoneOffset.UTC);}
    public void issue(LocalDateTime due){status=InvoiceStatus.ISSUED;issuedAt=LocalDateTime.now(ZoneOffset.UTC);dueAt=due;}
    public void markPaid(){status=InvoiceStatus.PAID;}
    public void voidInvoice(){status=InvoiceStatus.VOID;}
    public void snapshotPartCost(BigDecimal value) { this.repairPartsCostSnapshot = value; }
    public String getRepairTechnicianSnapshot() { return repairTechnicianSnapshot; }
    public String getRepairDiagnosisSnapshot() { return repairDiagnosisSnapshot; }
    public String getRepairWorkSnapshot() { return repairWorkSnapshot; }
    public String getRepairDeviceSnapshot() { return repairDeviceSnapshot; }
    public BigDecimal getRepairLaborCostSnapshot() { return repairLaborCostSnapshot; }
    public BigDecimal getRepairPartsCostSnapshot() { return repairPartsCostSnapshot; }
    public Long getId(){return id;}
    public Company getCompany(){return company;}
    public User getCustomer(){return customer;}
    public CustomerOrder getOrder(){return order;}
    public RepairRequest getRepair(){return repair;}
    public String getInvoiceNumber(){return invoiceNumber;}
    public InvoiceStatus getStatus(){return status;}
    public BigDecimal getSubtotal(){return subtotal;}
    public BigDecimal getTaxAmount(){return taxAmount;}
    public BigDecimal getTotalAmount(){return totalAmount;}
    public LocalDateTime getIssuedAt(){return issuedAt;}
    public LocalDateTime getDueAt(){return dueAt;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
