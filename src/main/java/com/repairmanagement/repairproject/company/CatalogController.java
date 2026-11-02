package com.repairmanagement.repairproject.company;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CompanyRepository companies;
    public CatalogController(CompanyRepository companies) { this.companies = companies; }
    @GetMapping("/companies")
    public List<PublicCompanyView> companies() {
        return companies.findByStatusOrderByNameAsc(CompanyStatus.ACTIVE)
            .stream().map(PublicCompanyView::of).toList();
    }
}
