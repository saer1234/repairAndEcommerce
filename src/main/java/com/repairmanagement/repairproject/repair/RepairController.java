package com.repairmanagement.repairproject.repair;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class RepairController {
    private final RepairService service;

    public RepairController(RepairService service) { this.service = service; }

    @PostMapping("/repairs")
    @ResponseStatus(HttpStatus.CREATED)
    public RepairView create(@PathVariable Long companyId, @Valid @RequestBody CreateRepairRequest body,
                             Principal principal) {
        return service.create(companyId, body, principal.getName());
    }

    @GetMapping("/repairs/eligible-clients")
    public List<RepairClientOption> eligibleClients(@PathVariable Long companyId,
                                                     @RequestParam String q, Principal principal) {
        return service.searchClients(companyId, q, principal.getName());
    }

    @PostMapping("/repairs/for-client")
    @ResponseStatus(HttpStatus.CREATED)
    public RepairView createForClient(@PathVariable Long companyId,
        @Valid @RequestBody CreateRepairForClientRequest body, Principal principal) {
        return service.createForClient(companyId, body, principal.getName());
    }

    @GetMapping("/repairs/mine")
    public List<RepairView> mine(@PathVariable Long companyId, Principal principal) {
        return service.mine(companyId, principal.getName());
    }

    @GetMapping("/repairs")
    public List<RepairView> companyRepairs(@PathVariable Long companyId, Principal principal) {
        return service.companyRepairs(companyId, principal.getName());
    }

    @GetMapping("/repairs/{repairId}")
    public RepairView one(@PathVariable Long companyId, @PathVariable Long repairId, Principal principal) {
        return service.get(companyId, repairId, principal.getName());
    }

    @GetMapping("/repairs/{repairId}/manage")
    public RepairView staffDetail(@PathVariable Long companyId, @PathVariable Long repairId,
                                  Principal principal) {
        return service.getForStaff(companyId, repairId, principal.getName());
    }

    @PutMapping("/repairs/{repairId}/assignment")
    public RepairView assign(@PathVariable Long companyId, @PathVariable Long repairId,
                             @Valid @RequestBody AssignRepairRequest body, Principal principal) {
        return service.assign(companyId, repairId, body, principal.getName());
    }

    @PostMapping("/repairs/{repairId}/status")
    public RepairView changeStatus(@PathVariable Long companyId, @PathVariable Long repairId,
                                   @Valid @RequestBody ChangeRepairStatusRequest body,
                                   Principal principal) {
        return service.changeStatus(companyId, repairId, body, principal.getName());
    }

    @PostMapping("/repairs/{repairId}/progress")
    public RepairView progress(@PathVariable Long companyId, @PathVariable Long repairId,
                               @Valid @RequestBody PublishRepairProgressRequest body, Principal principal) {
        return service.publishProgress(companyId, repairId, body, principal.getName());
    }

    @PostMapping("/repairs/{repairId}/parts")
    public RepairView addPart(@PathVariable Long companyId, @PathVariable Long repairId,
                              @Valid @RequestBody AddRepairPartRequest body, Principal principal) {
        return service.addPart(companyId, repairId, body, principal.getName());
    }

    @DeleteMapping("/repairs/{repairId}/parts/{partId}")
    public RepairView returnPart(@PathVariable Long companyId, @PathVariable Long repairId,
                                 @PathVariable Long partId, Principal principal) {
        return service.returnPart(companyId, repairId, partId, principal.getName());
    }

    @PutMapping("/repairs/{repairId}/labor")
    public RepairView updateLabor(@PathVariable Long companyId, @PathVariable Long repairId,
                                  @Valid @RequestBody UpdateLaborRequest body, Principal principal) {
        return service.updateLabor(companyId, repairId, body, principal.getName());
    }

    @PutMapping("/repairs/{repairId}/report")
    public RepairView updateReport(@PathVariable Long companyId, @PathVariable Long repairId,
        @Valid @RequestBody UpdateRepairReportRequest body, Principal principal) {
        return service.updateRepairReport(companyId, repairId, body, principal.getName());
    }

    @GetMapping("/finance/repair-summary")
    public RepairProfitSummaryView profitSummary(@PathVariable Long companyId, Principal principal) {
        return service.profitSummary(companyId, principal.getName());
    }
}
