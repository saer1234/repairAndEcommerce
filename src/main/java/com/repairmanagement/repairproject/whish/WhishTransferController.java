package com.repairmanagement.repairproject.whish;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class WhishTransferController {
    private final WhishTransferService service;
    public WhishTransferController(WhishTransferService service){this.service=service;}

    @PutMapping("/whish/settings")
    public Map<String,String> setWallet(@PathVariable Long companyId,
        @Valid @RequestBody WhishWalletRequest req,Principal principal){
        return Map.of("phone",service.configureWallet(companyId,req,principal.getName()));
    }
    @GetMapping("/whish/settings")
    public Map<String,String> settings(@PathVariable Long companyId,Principal principal){
        String phone=service.managerWallet(companyId,principal.getName());
        return Map.of("phone",phone==null?"":phone);
    }
    @GetMapping("/orders/{orderId}/whish/instructions")
    public WhishInstructions orderInstructions(@PathVariable Long companyId,@PathVariable Long orderId,Principal p){
        return service.orderInstructions(companyId,orderId,p.getName());
    }
    @PostMapping("/orders/{orderId}/whish/transfers") @ResponseStatus(HttpStatus.CREATED)
    public WhishView submitOrder(@PathVariable Long companyId,@PathVariable Long orderId,
        @Valid @RequestBody WhishReferenceRequest body,Principal p){
        return service.submitOrder(companyId,orderId,body,p.getName());
    }
    @GetMapping("/orders/{orderId}/whish/transfers")
    public List<WhishView> orderTransfers(@PathVariable Long companyId,@PathVariable Long orderId,Principal p){
        return service.orderTransfers(companyId,orderId,p.getName());
    }
    @GetMapping("/invoices/{invoiceId}/whish/instructions")
    public WhishInstructions invoiceInstructions(@PathVariable Long companyId,@PathVariable Long invoiceId,Principal p){
        return service.invoiceInstructions(companyId,invoiceId,p.getName());
    }
    @PostMapping("/invoices/{invoiceId}/whish/transfers") @ResponseStatus(HttpStatus.CREATED)
    public WhishView submitInvoice(@PathVariable Long companyId,@PathVariable Long invoiceId,
        @Valid @RequestBody WhishReferenceRequest body,Principal p){
        return service.submitInvoice(companyId,invoiceId,body,p.getName());
    }
    @GetMapping("/invoices/{invoiceId}/whish/transfers")
    public List<WhishView> invoiceTransfers(@PathVariable Long companyId,@PathVariable Long invoiceId,Principal p){
        return service.invoiceTransfers(companyId,invoiceId,p.getName());
    }
    @GetMapping("/whish/transfers")
    public List<WhishView> forManager(@PathVariable Long companyId,Principal p){
        return service.pendingForManager(companyId,p.getName());
    }
    @PostMapping("/whish/transfers/{transferId}/verify")
    public WhishView verify(@PathVariable Long companyId,@PathVariable Long transferId,Principal p){
        return service.verify(companyId,transferId,p.getName());
    }
    @PostMapping("/whish/transfers/{transferId}/reject")
    public WhishView reject(@PathVariable Long companyId,@PathVariable Long transferId,
        @Valid @RequestBody WhishReviewRequest body,Principal p){
        return service.reject(companyId,transferId,body,p.getName());
    }
}
