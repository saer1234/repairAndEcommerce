package com.repairmanagement.repairproject.order;

import java.security.Principal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView checkout(@PathVariable Long companyId,
        @Valid @RequestBody CheckoutRequest body, Principal principal) {
        return service.checkout(companyId, body, principal.getName());
    }

    @GetMapping("/orders/mine")
    public List<OrderView> mine(@PathVariable Long companyId, Principal principal) {
        return service.mine(companyId, principal.getName());
    }

    @GetMapping("/orders")
    public List<OrderView> all(@PathVariable Long companyId, Principal principal) {
        return service.all(companyId, principal.getName());
    }

    @GetMapping("/orders/{orderId}")
    public OrderView one(@PathVariable Long companyId, @PathVariable Long orderId, Principal principal) {
        return service.get(companyId, orderId, principal.getName());
    }

    @PostMapping("/orders/{orderId}/complete")
    public OrderView complete(@PathVariable Long companyId, @PathVariable Long orderId, Principal principal) {
        return service.complete(companyId, orderId, principal.getName());
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable Long companyId, @PathVariable Long orderId, Principal principal) {
        return service.cancel(companyId, orderId, principal.getName());
    }

    @GetMapping("/orders/{orderId}/profit")
    public OrderProfitView profit(@PathVariable Long companyId, @PathVariable Long orderId, Principal principal) {
        return service.orderProfit(companyId, orderId, principal.getName());
    }

    @GetMapping("/finance/sales-summary")
    public SalesSummaryView summary(@PathVariable Long companyId, Principal principal) {
        return service.salesSummary(companyId, principal.getName());
    }
}
