package com.repairmanagement.repairproject.whish;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.order.CustomerOrder;
import com.repairmanagement.repairproject.finance.Invoice;
import com.repairmanagement.repairproject.finance.Payment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name="whish_transfers")
public class WhishTransfer {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id") private User customer;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private CustomerOrder order;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="invoice_id") private Invoice invoice;
  @Column(name="transfer_reference",nullable=false,length=120) private String transferReference;
  @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
  @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
  @Column(name="receiver_phone_snapshot",nullable=false,length=40) private String receiverPhoneSnapshot;
  @Column(name="sender_name",length=150) private String senderName;
  @Column(name="sender_phone",length=40) private String senderPhone;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private WhishStatus status=WhishStatus.PENDING;
  @Column(name="submitted_at",nullable=false) private LocalDateTime submittedAt;
  @Column(name="reviewed_at") private LocalDateTime reviewedAt;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by_user_id") private User reviewedBy;
  @Column(name="review_note",length=500) private String reviewNote;
  @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="reconciled_payment_id") private Payment reconciledPayment;

  protected WhishTransfer() {}
  public WhishTransfer(Company company,User customer,CustomerOrder order,Invoice invoice,
                       String reference,BigDecimal amount,String senderName,String senderPhone){
    this.company=company;this.customer=customer;this.order=order;this.invoice=invoice;
    this.transferReference=reference;this.amount=amount;this.currencyCode=company.getCurrencyCode();
    this.receiverPhoneSnapshot=company.getWhishWalletPhone();
    this.senderName=senderName.trim();this.senderPhone=senderPhone.trim();
  }
  @PrePersist void onCreate(){submittedAt=LocalDateTime.now(ZoneOffset.UTC);}
  public void verify(User reviewer){this.status=WhishStatus.VERIFIED;this.reviewedAt=LocalDateTime.now(ZoneOffset.UTC);this.reviewedBy=reviewer;}
  public void reject(User reviewer,String reason){this.status=WhishStatus.REJECTED;this.reviewedAt=LocalDateTime.now(ZoneOffset.UTC);this.reviewedBy=reviewer;this.reviewNote=reason;}
  public void reconcile(Payment payment){if(this.reconciledPayment!=null)throw new IllegalStateException("Already reconciled");this.reconciledPayment=payment;}
  public Long getId(){return id;}
  public Company getCompany(){return company;}
  public User getCustomer(){return customer;}
  public CustomerOrder getOrder(){return order;}
  public Invoice getInvoice(){return invoice;}
  public String getTransferReference(){return transferReference;}
  public BigDecimal getAmount(){return amount;}
  public String getCurrencyCode(){return currencyCode;}
  public String getReceiverPhoneSnapshot(){return receiverPhoneSnapshot;}
  public String getSenderName(){return senderName;}
  public String getSenderPhone(){return senderPhone;}
  public WhishStatus getStatus(){return status;}
  public LocalDateTime getSubmittedAt(){return submittedAt;}
  public LocalDateTime getReviewedAt(){return reviewedAt;}
  public String getReviewNote(){return reviewNote;}
  public Payment getReconciledPayment(){return reconciledPayment;}
}
