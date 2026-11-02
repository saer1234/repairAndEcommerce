package com.repairmanagement.repairproject.finance;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/companies/{companyId}/repairs/{repairId}")
public class RepairInvoiceReportController {
    private final RepairInvoiceReportService service;
    public RepairInvoiceReportController(RepairInvoiceReportService service) { this.service=service; }
    @GetMapping("/invoice-report")
    public RepairInvoiceReport report(@PathVariable Long companyId,@PathVariable Long repairId,Principal p) {
        return service.customerReport(companyId,repairId,p.getName());
    }
    @GetMapping("/invoice-report/internal")
    public RepairInvoiceInternalReport internal(@PathVariable Long companyId,@PathVariable Long repairId,Principal p) {
        return service.internalReport(companyId,repairId,p.getName());
    }
}
