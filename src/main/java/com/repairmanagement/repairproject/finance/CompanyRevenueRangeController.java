package com.repairmanagement.repairproject.finance;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/finance")
public class CompanyRevenueRangeController {
    private final CompanyRevenueRangeService service;
    public CompanyRevenueRangeController(CompanyRevenueRangeService service) {this.service=service;}
    @GetMapping("/date-range")
    public CompanyRevenueRangeView range(@PathVariable Long companyId,
       @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
       @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, Principal principal) {
        return service.report(companyId,from,to,principal.getName());
    }
}
