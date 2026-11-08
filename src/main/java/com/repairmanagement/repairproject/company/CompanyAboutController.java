package com.repairmanagement.repairproject.company;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CompanyAboutController {
    private final CompanyAboutService service;
    public CompanyAboutController(CompanyAboutService service) { this.service = service; }

    @GetMapping("/api/catalog/companies/{companyId}/about")
    public CompanyAboutView read(@PathVariable Long companyId) { return service.publicAbout(companyId); }

    @PutMapping("/api/companies/{companyId}/about")
    public CompanyAboutView update(@PathVariable Long companyId,
                                  @Valid @RequestBody CompanyAboutRequest request,
                                  Principal principal) {
        return service.update(companyId, request, principal.getName());
    }
}
