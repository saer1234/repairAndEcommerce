package com.repairmanagement.repairproject.whish;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.finance.*;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.shopping.ShoppingEligibility;
import com.repairmanagement.repairproject.user.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Manual bank-wallet settlement: NEVER trusts customer-supplied proof as actual payment. */
@Service
public class WhishTransferService {
    private final InventoryAccess access;
    private final UserService users;
    private final ShoppingEligibility shoppers;
    private final CompanyRepository companies;
    private final CustomerOrderRepository orders;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final WhishTransferRepository transfers;

    public WhishTransferService(InventoryAccess access, UserService users, ShoppingEligibility shoppers,
            CompanyRepository companies, CustomerOrderRepository orders, InvoiceRepository invoices,
            PaymentRepository payments, WhishTransferRepository transfers) {
        this.access=access; this.users=users; this.shoppers=shoppers; this.companies=companies;
        this.orders=orders; this.invoices=invoices; this.payments=payments; this.transfers=transfers;
    }

    @Transactional
    public String configureWallet(Long companyId,WhishWalletRequest request,String email) {
        access.requireManager(companyId,email);
        Company c=access.requireActiveCompany(companyId);
        // The wallet must actually belong to the shop. Real verification takes place outside this app.
        String phone=request.phone().replace(" ","").trim();
        c.setWhishWalletPhone(phone);
        return c.getWhishWalletPhone();
    }

    @Transactional(readOnly=true)
    public String managerWallet(Long companyId,String email) {
        access.requireManager(companyId,email);
        return access.requireActiveCompany(companyId).getWhishWalletPhone();
    }

    @Transactional(readOnly=true)
    public WhishInstructions orderInstructions(Long companyId,Long orderId,String email) {
        CustomerOrder order=readOrder(companyId,orderId,email);
        ensureOrderCanAcceptTransfer(order);
        if(invoices.findByCompany_IdAndOrder_Id(companyId,orderId).map(i->i.getStatus()==InvoiceStatus.ISSUED || i.getStatus()==InvoiceStatus.PAID).orElse(false))
            throw conflict("Use the issued invoice to submit an outstanding payment");
        return new WhishInstructions(wallet(order.getCompany()),order.getTotalAmount(),
            order.getCompany().getCurrencyCode(),order.getOrderNumber(),null);
    }

    @Transactional
    public WhishView submitOrder(Long companyId,Long orderId,WhishReferenceRequest request,String email) {
        User customer=shoppers.requireClient(email);
        CustomerOrder order=orders.lockForUpdate(companyId,orderId).orElseThrow(()->missing("Order"));
        requireOwnerOfOrder(order,customer);
        ensureOrderCanAcceptTransfer(order);
        if (invoices.existsByOrder_Id(orderId)) {
            Invoice inv=invoices.findByCompany_IdAndOrder_Id(companyId,orderId).orElseThrow(()->missing("Invoice"));
            if(inv.getStatus()!=InvoiceStatus.DRAFT)
                throw conflict("Use the issued invoice for payment, not the original order");
        }
        wallet(order.getCompany());
        checkExisting(transfers.findByOrder_IdOrderBySubmittedAtDesc(orderId));
        String reference=uniqueReference(companyId,request.reference());
        return WhishView.from(transfers.saveAndFlush(new WhishTransfer(order.getCompany(),customer,order,null,
             reference,order.getTotalAmount(),request.senderName(),request.senderPhone())));
    }

    @Transactional(readOnly=true)
    public WhishInstructions invoiceInstructions(Long companyId,Long invoiceId,String email) {
        Invoice inv=readInvoice(companyId,invoiceId,email);
        if(inv.getStatus()!=InvoiceStatus.ISSUED)throw conflict("Invoice must be ISSUED and unpaid");
        ensureNoVerifiedOrderClaim(inv);
        BigDecimal remaining=remainingAmount(inv);
        if(remaining.signum()<=0)throw conflict("Invoice has no outstanding balance");
        return new WhishInstructions(wallet(inv.getCompany()),remaining,inv.getCompany().getCurrencyCode(),
            inv.getOrder()==null?null:inv.getOrder().getOrderNumber(),inv.getInvoiceNumber());
    }

    @Transactional
    public WhishView submitInvoice(Long companyId,Long invoiceId,WhishReferenceRequest request,String email) {
        User customer=shoppers.requireClient(email);
        Invoice inv=invoices.lockForUpdate(companyId,invoiceId).orElseThrow(()->missing("Invoice"));
        if(!inv.getCustomer().getId().equals(customer.getId()))throw missing("Invoice");
        if(inv.getStatus()!=InvoiceStatus.ISSUED)throw conflict("Invoice must be ISSUED and unpaid");
        ensureNoVerifiedOrderClaim(inv);
        wallet(inv.getCompany());
        checkExisting(transfers.findByInvoice_IdOrderBySubmittedAtDesc(invoiceId));
        BigDecimal remaining=remainingAmount(inv);
        if(remaining.signum()<=0)throw conflict("Invoice has no outstanding balance");
        String reference=uniqueReference(companyId,request.reference());
        return WhishView.from(transfers.saveAndFlush(new WhishTransfer(inv.getCompany(),customer,null,inv,
            reference,remaining,request.senderName(),request.senderPhone())));
    }

    @Transactional(readOnly=true)
    public List<WhishView> orderTransfers(Long companyId,Long orderId,String email) {
        readOrder(companyId,orderId,email);
        return transfers.findByOrder_IdOrderBySubmittedAtDesc(orderId).stream().map(WhishView::from).toList();
    }

    @Transactional(readOnly=true)
    public List<WhishView> invoiceTransfers(Long companyId,Long invoiceId,String email) {
        readInvoice(companyId,invoiceId,email);
        return transfers.findByInvoice_IdOrderBySubmittedAtDesc(invoiceId).stream().map(WhishView::from).toList();
    }

    @Transactional(readOnly=true)
    public List<WhishView> pendingForManager(Long companyId,String email) {
        access.requireManager(companyId,email);
        return transfers.findByCompany_IdOrderBySubmittedAtDesc(companyId).stream().map(WhishView::from).toList();
    }

    @Transactional
    public WhishView verify(Long companyId,Long transferId,String email) {
        User reviewer=access.requireManager(companyId,email);
        WhishTransfer transfer=transfers.lockTransfer(companyId,transferId).orElseThrow(()->missing("Transfer"));
        if(transfer.getStatus()!=WhishStatus.PENDING)throw conflict("Transfer already reviewed");
        // The manager must verify receipt in the REAL Whish wallet, not merely inspect a reference.
        Invoice invoice=transfer.getInvoice();
        if(transfer.getOrder()!=null) {
            CustomerOrder order=orders.lockForUpdate(companyId,transfer.getOrder().getId())
                .orElseThrow(()->missing("Order"));
            ensureOrderCanAcceptTransfer(order);
            if(invoices.existsByOrder_Id(order.getId()))
                invoice=invoices.findByCompany_IdAndOrder_Id(companyId,order.getId()).orElseThrow(()->missing("Invoice"));
        }
        if(invoice!=null) {
            invoice=invoices.lockForUpdate(companyId,invoice.getId()).orElseThrow(()->missing("Invoice"));
            if(invoice.getStatus()==InvoiceStatus.VOID || invoice.getStatus()==InvoiceStatus.PAID)
                throw conflict("Invoice is void or already paid");
            // An order claim can be approved before the order invoice is issued.
            if(invoice.getStatus()==InvoiceStatus.ISSUED && transfer.getAmount().compareTo(remainingAmount(invoice))>0)
                throw conflict("Transfer exceeds invoice's unpaid amount; reconcile manually");
        }
        transfer.verify(reviewer);
        if(invoice!=null && invoice.getStatus()==InvoiceStatus.ISSUED)
            applyToInvoice(transfer,invoice);
        return WhishView.from(transfer);
    }

    @Transactional
    public WhishView reject(Long companyId,Long transferId,WhishReviewRequest body,String email) {
        User reviewer=access.requireManager(companyId,email);
        WhishTransfer transfer=transfers.lockTransfer(companyId,transferId).orElseThrow(()->missing("Transfer"));
        if(transfer.getStatus()!=WhishStatus.PENDING)throw conflict("Transfer already reviewed");
        transfer.reject(reviewer,body.note().trim());
        return WhishView.from(transfer);
    }

    /** Called when the manager issues an invoice for a previously verified order transfer. */
    @Transactional
    public void reconcileVerifiedOrderTransfer(Invoice invoice) {
        if(invoice.getOrder()==null)return;
        for(WhishTransfer transfer:transfers.findByOrder_IdOrderBySubmittedAtDesc(invoice.getOrder().getId())) {
            if(transfer.getStatus()!=WhishStatus.VERIFIED || transfer.getReconciledPayment()!=null)continue;
            if(transfer.getAmount().compareTo(remainingAmount(invoice))>0)
                throw conflict("Verified order deposit exceeds invoice outstanding; adjust the invoice before issue");
            applyToInvoice(transfer,invoice);
        }
    }

    private void applyToInvoice(WhishTransfer transfer,Invoice invoice) {
        if(transfer.getReconciledPayment()!=null)return;
        if(invoice.getStatus()!=InvoiceStatus.ISSUED)throw conflict("Invoice must be issued");
        if(!invoice.getCustomer().getId().equals(transfer.getCustomer().getId()) ||
           !invoice.getCompany().getId().equals(transfer.getCompany().getId()))throw conflict("Payment ownership mismatch");
        if(payments.existsByInvoice_IdAndProviderReference(invoice.getId(),transfer.getTransferReference()))
           throw conflict("Payment already recorded");
        if(transfer.getAmount().compareTo(remainingAmount(invoice))>0)throw conflict("Payment exceeds invoice balance");
        Payment p=payments.saveAndFlush(new Payment(invoice,transfer.getAmount(),PaymentMethod.WHISH_TRANSFER,
            transfer.getTransferReference()));
        transfer.reconcile(p);
        if(remainingAmount(invoice).signum()==0)invoice.markPaid();
    }

    private void checkExisting(List<WhishTransfer> list) {
        if(list.stream().anyMatch(t->t.getStatus()!=WhishStatus.REJECTED))
            throw conflict("A pending or verified Whish submission already exists for this order/invoice");
    }
    private void ensureNoVerifiedOrderClaim(Invoice invoice) {
        if(invoice.getOrder()!=null && transfers.findByOrder_IdOrderBySubmittedAtDesc(invoice.getOrder().getId())
            .stream().anyMatch(t->t.getStatus()==WhishStatus.PENDING ||
               (t.getStatus()==WhishStatus.VERIFIED && t.getReconciledPayment()==null)))
            throw conflict("Order has a verified advance transfer awaiting invoice reconciliation");
    }
    private String uniqueReference(Long companyId,String raw) {
        String normalized=raw.trim().toUpperCase(Locale.ROOT);
        if(transfers.existsByCompany_IdAndTransferReference(companyId,normalized))
            throw conflict("Transfer reference already submitted");
        return normalized;
    }
    private BigDecimal remainingAmount(Invoice invoice) {
        BigDecimal received=payments.findByInvoice_IdOrderByIdAsc(invoice.getId()).stream()
            .filter(p->p.getStatus()==PaymentStatus.COMPLETED).map(Payment::getAmount)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        return invoice.getTotalAmount().subtract(received);
    }
    private CustomerOrder readOrder(Long companyId,Long id,String email) {
        User actor=users.currentUser(email);
        access.requireActiveCompany(companyId);
        CustomerOrder order=orders.findByIdAndCompany_Id(id,companyId).orElseThrow(()->missing("Order"));
        if(!order.getCustomer().getId().equals(actor.getId()))access.requireManager(companyId,email);
        return order;
    }
    private Invoice readInvoice(Long companyId,Long id,String email) {
        User actor=users.currentUser(email);
        access.requireActiveCompany(companyId);
        Invoice inv=invoices.findByIdAndCompany_Id(id,companyId).orElseThrow(()->missing("Invoice"));
        if(!inv.getCustomer().getId().equals(actor.getId()))access.requireManager(companyId,email);
        return inv;
    }
    private void requireOwnerOfOrder(CustomerOrder order,User customer) {
        if(!order.getCustomer().getId().equals(customer.getId()))throw missing("Order");
    }
    private void ensureOrderCanAcceptTransfer(CustomerOrder order) {
        if(order.getStatus()==OrderStatus.CANCELLED || order.getStatus()==OrderStatus.REFUNDED)
            throw conflict("Cannot pay a cancelled or refunded order");
    }
    private String wallet(Company company) {
        String wallet=company.getWhishWalletPhone();
        if(wallet==null || wallet.isBlank())throw conflict("Shop has not configured a Whish receiving number");
        return wallet;
    }
    private static ResponseStatusException missing(String thing){return new ResponseStatusException(HttpStatus.NOT_FOUND,thing+" not found");}
    private static ResponseStatusException conflict(String reason){return new ResponseStatusException(HttpStatus.CONFLICT,reason);}
}
