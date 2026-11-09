package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.repair.*;
import com.repairmanagement.repairproject.user.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinanceService {
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private final InventoryAccess access;
    private final UserService userService;
    private final CompanyRepository companies;
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final RepairRequestRepository repairs;
    private final RepairPartRepository repairParts;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final PlatformFeeRepository fees;
    private final OrderService orderService;
    private final RepairService repairService;
    private final com.repairmanagement.repairproject.whish.WhishTransferService whishService;

    public FinanceService(InventoryAccess access,UserService userService,CompanyRepository companies,
        CustomerOrderRepository orders,OrderItemRepository orderItems,RepairRequestRepository repairs,
        RepairPartRepository repairParts,InvoiceRepository invoices,InvoiceLineRepository invoiceLines,
        PaymentRepository payments,ExpenseRepository expenses,PlatformFeeRepository fees,
        OrderService orderService,RepairService repairService,
        com.repairmanagement.repairproject.whish.WhishTransferService whishService){
        this.access=access;this.userService=userService;this.companies=companies;this.orders=orders;
        this.orderItems=orderItems;this.repairs=repairs;this.repairParts=repairParts;this.invoices=invoices;
        this.invoiceLines=invoiceLines;this.payments=payments;this.expenses=expenses;this.fees=fees;
        this.orderService=orderService;this.repairService=repairService;this.whishService=whishService;
    }

    @Transactional
    public InvoiceView createInvoice(Long companyId,CreateInvoiceRequest req,String email){
        access.requireManager(companyId,email);
        Company company=access.requireActiveCompany(companyId);
        boolean isOrder=req.orderId()!=null, isRepair=req.repairId()!=null;
        if(isOrder==isRepair) throw bad("Specify exactly one: orderId or repairId");
        User customer;
        CustomerOrder order=null;
        RepairRequest repair=null;
        List<InvoiceLine> lines=new ArrayList<>();
        // Lock the source so concurrent attempts to invoice it serialize.
        if(isOrder){
            order=orders.lockForUpdate(companyId,req.orderId()).orElseThrow(()->notFound("Order"));
            if(order.getStatus()!=OrderStatus.COMPLETED) throw conflict("Complete the order before invoicing");
            if(invoices.existsByOrder_Id(order.getId())) throw conflict("Order already has an invoice");
            customer=order.getCustomer();
        }else{
            repair=repairs.lockForUpdate(companyId,req.repairId()).orElseThrow(()->notFound("Repair"));
            if(repair.getStatus()!=RepairStatus.COMPLETED && repair.getStatus()!=RepairStatus.DELIVERED)
                throw conflict("Complete the repair before invoicing");
            if(invoices.existsByRepair_Id(repair.getId())) throw conflict("Repair already has an invoice");
            if (repair.getAssignedEmployee() == null ||
                repair.getDiagnosis() == null || repair.getDiagnosis().isBlank() ||
                repair.getWorkPerformed() == null || repair.getWorkPerformed().isBlank()) {
                throw conflict("Complete the technician assignment and written diagnosis/work report before invoicing");
            }
            customer=repair.getCustomer();
        }
        BigDecimal subtotal=calculateSubtotal(order,repair);
        if(subtotal.add(req.taxAmount()).signum()<=0)
            throw bad("Cannot issue a zero-value invoice");
        Invoice invoice=new Invoice(company,customer,order,repair,
            "INV-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT),
            subtotal,req.taxAmount());
        if (repair != null) {
            BigDecimal totalPartCost = repairParts.findByRepair_IdOrderByIdAsc(repair.getId()).stream()
                .map(p -> p.getUnitCostPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(ZERO, BigDecimal::add);
            invoice.snapshotPartCost(totalPartCost);
        }
        invoices.saveAndFlush(invoice);
        if(isOrder){
            for(OrderItem i:orderItems.findByOrder_IdOrderByIdAsc(order.getId())){
                lines.add(new InvoiceLine(invoice,i.getItemNameSnapshot(),BigDecimal.valueOf(i.getQuantity()),i.getUnitSellingPrice()));
            }
            if (order.getDeliveryFee().signum() > 0)
                lines.add(new InvoiceLine(invoice,"Delivery fee",BigDecimal.ONE,order.getDeliveryFee()));
        }else{
            for(RepairPart part:repairParts.findByRepair_IdOrderByIdAsc(repair.getId())){
                lines.add(new InvoiceLine(invoice,"Repair part: "+part.getItem().getName(),
                    BigDecimal.valueOf(part.getQuantity()),part.getUnitSellingPrice()));
            }
            if(repair.getLaborCharge().signum()>0)lines.add(new InvoiceLine(invoice,"Repair labor",BigDecimal.ONE,repair.getLaborCharge()));
        }
        invoiceLines.saveAll(lines);
        return view(invoice);
    }

    private BigDecimal calculateSubtotal(CustomerOrder order,RepairRequest repair){
        if(order!=null){
            return orderItems.findByOrder_IdOrderByIdAsc(order.getId()).stream()
                .map(x->x.getUnitSellingPrice().multiply(BigDecimal.valueOf(x.getQuantity())))
                .reduce(ZERO,BigDecimal::add).add(order.getDeliveryFee());
        }
        BigDecimal subtotal=repair.getLaborCharge();
        for(RepairPart p:repairParts.findByRepair_IdOrderByIdAsc(repair.getId()))
            subtotal=subtotal.add(p.getUnitSellingPrice().multiply(BigDecimal.valueOf(p.getQuantity())));
        return subtotal;
    }

    @Transactional
    public InvoiceView issueInvoice(Long companyId,Long invoiceId,IssueInvoiceRequest body,String email){
        access.requireManager(companyId,email);
        Invoice invoice=lockInvoice(companyId,invoiceId);
        if(invoice.getStatus()!=InvoiceStatus.DRAFT)throw conflict("Only draft invoices can be issued");
        if(!body.dueAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))throw bad("dueAt must be a future UTC time");
        invoice.issue(body.dueAt());
        // A verified advance order transfer is applied only once an invoice exists.
        whishService.reconcileVerifiedOrderTransfer(invoice);
        return view(invoice);
    }

    @Transactional
    public InvoiceView voidInvoice(Long companyId,Long invoiceId,String email){
        access.requireManager(companyId,email);
        Invoice invoice=lockInvoice(companyId,invoiceId);
        if(invoice.getStatus()!=InvoiceStatus.DRAFT && invoice.getStatus()!=InvoiceStatus.ISSUED)
            throw conflict("Invoice cannot be voided");
        if(!payments.findByInvoice_IdOrderByIdAsc(invoiceId).isEmpty())
            throw conflict("Cannot void an invoice with payment records; reconcile first");
        invoice.voidInvoice();return view(invoice);
    }

    @Transactional
    public InvoiceView recordReceivedPayment(Long companyId,Long invoiceId,RecordPaymentRequest body,String email){
        access.requireManager(companyId,email);
        Invoice invoice=lockInvoice(companyId,invoiceId);
        if(invoice.getStatus()!=InvoiceStatus.ISSUED)throw conflict("Invoice must be ISSUED");
        // This is MANUAL confirmation of received funds. No card/online payment gateway here.
        if(body.method()!=PaymentMethod.CASH && body.method()!=PaymentMethod.BANK_TRANSFER)
            throw bad("Only manually confirmed CASH or BANK_TRANSFER payments are supported");
        String ref=body.reference().trim();
        if(payments.existsByInvoice_IdAndProviderReference(invoiceId,ref))
            throw conflict("Payment reference already recorded for this invoice");
        BigDecimal paid=completedPayments(invoiceId);
        BigDecimal remaining=invoice.getTotalAmount().subtract(paid);
        if(body.amount().compareTo(remaining)>0)throw conflict("Payment exceeds outstanding invoice amount");
        payments.saveAndFlush(new Payment(invoice,body.amount(),body.method(),ref));
        if(paid.add(body.amount()).compareTo(invoice.getTotalAmount())==0)invoice.markPaid();
        return view(invoice);
    }

    @Transactional(readOnly=true)
    public InvoiceView getInvoice(Long companyId,Long id,String email){
        User actor=userService.currentUser(email);
        Company company=access.requireActiveCompany(companyId);
        Invoice i=invoices.findByIdAndCompany_Id(id,company.getId()).orElseThrow(()->notFound("Invoice"));
        boolean manager=isManager(companyId,email);
        if(!manager && (!i.getCustomer().getId().equals(actor.getId()) ||
             (i.getStatus()!=InvoiceStatus.ISSUED && i.getStatus()!=InvoiceStatus.PAID)))
            throw notFound("Invoice");
        return view(i);
    }
    @Transactional(readOnly=true)
    public List<InvoiceView> companyInvoices(Long companyId,String email){
        access.requireManager(companyId,email);
        return invoices.findByCompany_IdOrderByCreatedAtDesc(companyId).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public List<InvoiceView> mine(Long companyId,String email){
        access.requireActiveCompany(companyId);
        User me=userService.currentUser(email);
        return invoices.findByCompany_IdAndCustomer_IdAndStatusNotOrderByCreatedAtDesc(companyId,me.getId(),InvoiceStatus.DRAFT)
            .stream().filter(i->i.getStatus()!=InvoiceStatus.VOID).map(this::view).toList();
    }
    @Transactional
    public ExpenseView addCompanyExpense(Long companyId,CreateExpenseRequest body,String email){
        User actor=access.requireManager(companyId,email);
        Company c=access.requireActiveCompany(companyId);
        if(body.expenseDate().isAfter(java.time.LocalDate.now(ZoneOffset.UTC)))
            throw bad("Actual expenses cannot have a future date");
        Expense expense=new Expense(c,body.category().trim(),body.description(),body.amount(),
            c.getCurrencyCode(),body.expenseDate(),actor);
        return ExpenseView.from(expenses.saveAndFlush(expense));
    }
    @Transactional(readOnly=true)
    public List<ExpenseView> companyExpenses(Long companyId,String email){
        access.requireManager(companyId,email);
        return expenses.findByCompany_IdOrderByExpenseDateDescIdDesc(companyId).stream().map(ExpenseView::from).toList();
    }
    @Transactional(readOnly=true)
    public CompanyFinanceView companyOverview(Long companyId,String email){
        access.requireManager(companyId,email);
        Company c=access.requireActiveCompany(companyId);
        SalesSummaryView sales=orderService.salesSummary(companyId,email);
        RepairProfitSummaryView repair=repairService.profitSummary(companyId,email);
        BigDecimal repairRevenue=repair.laborRevenue().add(repair.partsRevenue());
        BigDecimal costs=sales.costOfGoodsSold().add(repair.laborCost()).add(repair.partsCost());
        BigDecimal gross=sales.completedSalesRevenue().add(repairRevenue).subtract(costs);
        BigDecimal operating=expenses.findByCompany_IdOrderByExpenseDateDescIdDesc(companyId).stream()
           .map(Expense::getAmount).reduce(ZERO,BigDecimal::add);
        BigDecimal companyFees=fees.findByCompany_Id(companyId).stream().filter(f->f.getStatus()==FeeStatus.EARNED)
           .map(PlatformFee::getFeeAmount).reduce(ZERO,BigDecimal::add);
        BigDecimal collected=ZERO, taxCollected=ZERO, outstanding=ZERO;
        for(Invoice i:invoices.findByCompany_IdOrderByCreatedAtDesc(companyId)){
            if(i.getStatus()==InvoiceStatus.DRAFT || i.getStatus()==InvoiceStatus.VOID) continue;
            BigDecimal paid=completedPayments(i.getId());
            collected=collected.add(paid);
            if(i.getTotalAmount().signum()>0){
                taxCollected=taxCollected.add(i.getTaxAmount().multiply(paid)
                    .divide(i.getTotalAmount(),2,RoundingMode.HALF_UP));
            }
            outstanding=outstanding.add(i.getTotalAmount().subtract(paid));
        }
        return new CompanyFinanceView(companyId,c.getCurrencyCode(),sales.completedSalesRevenue(),repairRevenue,
             costs,gross,operating,companyFees,gross.subtract(operating).subtract(companyFees),
             collected,taxCollected,outstanding);
    }

    @Transactional
    public ExpenseView addPlatformExpense(CreatePlatformExpenseRequest body,String email){
        User actor=requirePlatformAdmin(email);
        if(body.expenseDate().isAfter(java.time.LocalDate.now(ZoneOffset.UTC)))
            throw bad("Actual expenses cannot have a future date");
        // No mixing currencies in reports: we group totals by ISO currency.
        try{java.util.Currency.getInstance(body.currencyCode());}
        catch(IllegalArgumentException e){throw bad("Invalid currency code");}
        Expense expense=new Expense(null,body.category().trim(),body.description(),body.amount(),
             body.currencyCode(),body.expenseDate(),actor);
        return ExpenseView.from(expenses.saveAndFlush(expense));
    }
    @Transactional
    public FeeView addPlatformFee(CreateFeeRequest body,String email){
        requirePlatformAdmin(email);
        Company company=access.requireActiveCompany(body.companyId());
        if(body.orderId()!=null && body.repairId()!=null) throw bad("Fee can refer to an order or repair, not both");
        if(body.feeType()==FeeType.COMMISSION && body.orderId()==null && body.repairId()==null)
            throw bad("Commission requires an order or repair source");
        if(body.feeType()!=FeeType.COMMISSION && (body.orderId()!=null || body.repairId()!=null))
            throw bad("Only commissions can be linked to orders and repairs");
        CustomerOrder o=null;RepairRequest r=null;
        if(body.orderId()!=null){
            o=orders.findByIdAndCompany_Id(body.orderId(),company.getId()).orElseThrow(()->notFound("Order"));
            if(o.getStatus()!=OrderStatus.COMPLETED)throw conflict("Commission source order must be completed");
        }
        if(body.repairId()!=null){
            r=repairs.findByIdAndCompany_Id(body.repairId(),company.getId()).orElseThrow(()->notFound("Repair"));
            if(r.getStatus()!=RepairStatus.COMPLETED && r.getStatus()!=RepairStatus.DELIVERED)
                throw conflict("Commission source repair must be completed");
        }
        return FeeView.from(fees.saveAndFlush(new PlatformFee(company,o,r,body.feeType(),body.amount())));
    }
    @Transactional
    public FeeView markFeeEarned(Long feeId,String email){
        requirePlatformAdmin(email);
        PlatformFee fee=fees.lockForUpdate(feeId).orElseThrow(()->notFound("Platform fee"));
        if(fee.getStatus()!=FeeStatus.PENDING)throw conflict("Fee already processed");
        fee.earn();return FeeView.from(fee);
    }
    @Transactional(readOnly=true)
    public List<PlatformCurrencySummary> platformOverview(String email){
        requirePlatformAdmin(email);
        Map<String,BigDecimal> income=new HashMap<>(),spend=new HashMap<>();
        for(PlatformFee f:fees.findAll()) if(f.getStatus()==FeeStatus.EARNED)
            income.merge(f.getCurrencyCode(),f.getFeeAmount(),BigDecimal::add);
        for(Expense e:expenses.findByCompanyIsNullOrderByExpenseDateDescIdDesc())
            spend.merge(e.getCurrencyCode(),e.getAmount(),BigDecimal::add);
        Set<String> currencies=new TreeSet<>(income.keySet());currencies.addAll(spend.keySet());
        return currencies.stream().map(currency->{
            BigDecimal rev=income.getOrDefault(currency,ZERO), cost=spend.getOrDefault(currency,ZERO);
            return new PlatformCurrencySummary(currency,rev,cost,rev.subtract(cost));
        }).toList();
    }

    private boolean isManager(Long companyId,String email){
        try{access.requireManager(companyId,email);return true;}
        catch(ResponseStatusException e){return false;}
    }
    private User requirePlatformAdmin(String email){
        User u=userService.currentUser(email);
        if(u.getPlatformRole()!=PlatformRole.SUPER_ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform administrator role required");
        return u;
    }
    private Invoice lockInvoice(Long companyId,Long id){
        return invoices.lockForUpdate(companyId,id).orElseThrow(()->notFound("Invoice"));
    }
    private BigDecimal completedPayments(Long id){
        return payments.findByInvoice_IdOrderByIdAsc(id).stream()
            .filter(p->p.getStatus()==PaymentStatus.COMPLETED).map(Payment::getAmount)
            .reduce(ZERO,BigDecimal::add);
    }
    private InvoiceView view(Invoice i){
        List<Payment> all=payments.findByInvoice_IdOrderByIdAsc(i.getId());
        BigDecimal paid=all.stream().filter(p->p.getStatus()==PaymentStatus.COMPLETED)
             .map(Payment::getAmount).reduce(ZERO,BigDecimal::add);
        return new InvoiceView(i.getId(),i.getInvoiceNumber(),i.getStatus(),i.getCustomer().getId(),
            i.getOrder()==null?null:i.getOrder().getId(),i.getRepair()==null?null:i.getRepair().getId(),
            i.getSubtotal(),i.getTaxAmount(),i.getTotalAmount(),paid,i.getTotalAmount().subtract(paid),
            i.getCompany().getCurrencyCode(),i.getIssuedAt(),i.getDueAt(),
            i.getCompany().getName(),i.getCompany().getAddress(),i.getCompany().getEmail(),i.getCompany().getPhone(),
            invoiceLines.findByInvoice_IdOrderByIdAsc(i.getId()).stream().map(InvoiceLineView::from).toList(),
            all.stream().map(PaymentView::from).toList());
    }
    private ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private ResponseStatusException conflict(String msg){return new ResponseStatusException(HttpStatus.CONFLICT,msg);}
    private ResponseStatusException notFound(String thing){return new ResponseStatusException(HttpStatus.NOT_FOUND,thing+" not found");}
}
