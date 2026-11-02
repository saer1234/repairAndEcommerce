package com.repairmanagement.repairproject.finance;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service){this.service=service;}
    @PostMapping("/invoices") @ResponseStatus(HttpStatus.CREATED)
    public InvoiceView create(@PathVariable Long companyId,@Valid @RequestBody CreateInvoiceRequest body,Principal p){
        return service.createInvoice(companyId,body,p.getName());
    }
    @GetMapping("/invoices")
    public List<InvoiceView> all(@PathVariable Long companyId,Principal p){return service.companyInvoices(companyId,p.getName());}
    @GetMapping("/invoices/mine")
    public List<InvoiceView> mine(@PathVariable Long companyId,Principal p){return service.mine(companyId,p.getName());}
    @GetMapping("/invoices/{invoiceId}")
    public InvoiceView one(@PathVariable Long companyId,@PathVariable Long invoiceId,Principal p){
        return service.getInvoice(companyId,invoiceId,p.getName());
    }
    @PostMapping("/invoices/{invoiceId}/issue")
    public InvoiceView issue(@PathVariable Long companyId,@PathVariable Long invoiceId,
        @Valid @RequestBody IssueInvoiceRequest body,Principal p){
        return service.issueInvoice(companyId,invoiceId,body,p.getName());
    }
    @PostMapping("/invoices/{invoiceId}/void")
    public InvoiceView voidInvoice(@PathVariable Long companyId,@PathVariable Long invoiceId,Principal p){
        return service.voidInvoice(companyId,invoiceId,p.getName());
    }
    @PostMapping("/invoices/{invoiceId}/payments") @ResponseStatus(HttpStatus.CREATED)
    public InvoiceView payment(@PathVariable Long companyId,@PathVariable Long invoiceId,
        @Valid @RequestBody RecordPaymentRequest body,Principal p){
        return service.recordReceivedPayment(companyId,invoiceId,body,p.getName());
    }
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView expense(@PathVariable Long companyId,@Valid @RequestBody CreateExpenseRequest body,Principal p){
        return service.addCompanyExpense(companyId,body,p.getName());
    }
    @GetMapping("/expenses")
    public List<ExpenseView> expenses(@PathVariable Long companyId,Principal p){
        return service.companyExpenses(companyId,p.getName());
    }
    @GetMapping("/finance/overview")
    public CompanyFinanceView overview(@PathVariable Long companyId,Principal p){
        return service.companyOverview(companyId,p.getName());
    }
}
