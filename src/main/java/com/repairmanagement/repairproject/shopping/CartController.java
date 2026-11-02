package com.repairmanagement.repairproject.shopping;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }

    @GetMapping
    public CartView get(@PathVariable Long companyId, Principal principal) {
        return service.get(companyId, principal.getName());
    }

    @PutMapping("/items/{itemId}")
    public CartView put(@PathVariable Long companyId, @PathVariable Long itemId,
                        @Valid @RequestBody UpdateCartItemRequest request, Principal principal) {
        return service.setQuantity(companyId, itemId, request.quantity(), principal.getName());
    }

    @DeleteMapping("/items/{itemId}")
    public CartView delete(@PathVariable Long companyId, @PathVariable Long itemId, Principal principal) {
        return service.remove(companyId, itemId, principal.getName());
    }
}
