package com.repairmanagement.repairproject.repair;

import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** These endpoints require JWT and always resolve ownership from the logged-in identity. */
@RestController
@RequestMapping("/api/repairs/tracking")
public class RepairTrackingController {
    private final RepairService service;
    public RepairTrackingController(RepairService service) { this.service = service; }

    @GetMapping
    public List<CustomerRepairTrackingView> mine(Principal principal) {
        return service.trackingMine(principal.getName());
    }

    @GetMapping("/{repairId}")
    public CustomerRepairTrackingView one(@PathVariable Long repairId, Principal principal) {
        return service.trackingOne(repairId, principal.getName());
    }
}
