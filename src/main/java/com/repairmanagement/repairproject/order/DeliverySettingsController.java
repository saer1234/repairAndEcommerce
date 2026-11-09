package com.repairmanagement.repairproject.order;
import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class DeliverySettingsController {
    private final InventoryAccess access;
    public DeliverySettingsController(InventoryAccess access) { this.access = access; }
    @GetMapping("/api/catalog/companies/{companyId}/delivery-settings")
    @Transactional(readOnly = true)
    public DeliverySettingsView read(@PathVariable Long companyId) {
        Company c = access.requireActiveCompany(companyId);
        return new DeliverySettingsView(c.isDeliveryEnabled(), c.getDeliveryFee(), c.getCurrencyCode());
    }
    @PutMapping("/api/companies/{companyId}/delivery-settings")
    @Transactional
    public DeliverySettingsView update(@PathVariable Long companyId,
        @Valid @RequestBody DeliverySettingsRequest request, Principal principal) {
        access.requireManager(companyId, principal.getName());
        Company c = access.requireActiveCompany(companyId);
        c.configureDelivery(request.deliveryEnabled(), request.deliveryFee());
        return new DeliverySettingsView(c.isDeliveryEnabled(), c.getDeliveryFee(), c.getCurrencyCode());
    }
}
