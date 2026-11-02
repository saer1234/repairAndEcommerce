package com.repairmanagement.repairproject.finance;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/platform/finance")
public class PlatformFinanceController {
    private final FinanceService service;
    public PlatformFinanceController(FinanceService service){this.service=service;}
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView expense(@Valid @RequestBody CreatePlatformExpenseRequest req,Principal p){
        return service.addPlatformExpense(req,p.getName());
    }
    @PostMapping("/fees") @ResponseStatus(HttpStatus.CREATED)
    public FeeView fee(@Valid @RequestBody CreateFeeRequest req,Principal p){
        return service.addPlatformFee(req,p.getName());
    }
    @PostMapping("/fees/{id}/earn")
    public FeeView earned(@PathVariable Long id,Principal p){return service.markFeeEarned(id,p.getName());}
    @GetMapping("/overview")
    public List<PlatformCurrencySummary> overview(Principal p){return service.platformOverview(p.getName());}
}
