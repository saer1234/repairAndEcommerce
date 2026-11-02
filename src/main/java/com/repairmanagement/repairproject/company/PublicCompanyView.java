package com.repairmanagement.repairproject.company;
public record PublicCompanyView(Long id, String name, String currencyCode) {
    public static PublicCompanyView of(Company company) {
        return new PublicCompanyView(company.getId(), company.getName(), company.getCurrencyCode());
    }
}
