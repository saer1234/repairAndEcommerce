package com.repairmanagement.repairproject.inventory;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/inventory")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }

    @GetMapping("/stock")
    public List<StockItemView> stock(@PathVariable Long companyId, Principal principal) {
        return service.stock(companyId, principal.getName());
    }

    @GetMapping("/items")
    public List<ManagerItemView> managerItems(@PathVariable Long companyId, Principal principal) {
        return service.managerItems(companyId, principal.getName());
    }

    @GetMapping("/summary")
    public InventorySummaryView summary(@PathVariable Long companyId, Principal principal) {
        return service.summary(companyId, principal.getName());
    }

    @GetMapping("/items/{itemId}/movements")
    public List<StockMovementView> movements(@PathVariable Long companyId, @PathVariable Long itemId,
                                             Principal principal) {
        return service.history(companyId, itemId, principal.getName());
    }

    @PostMapping("/items/{itemId}/movements")
    public StockItemView adjust(@PathVariable Long companyId, @PathVariable Long itemId,
                                @Valid @RequestBody StockAdjustmentRequest request, Principal principal) {
        return service.adjust(companyId, itemId, request, principal.getName());
    }
}
