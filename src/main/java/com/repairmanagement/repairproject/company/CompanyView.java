package com.repairmanagement.repairproject.company;

public record CompanyView(Long id, String name, String email, String phone,
                          String address, String currencyCode, CompanyStatus status,
                          MemberRole yourRole) {
    public static CompanyView of(Company c, MemberRole role) {
        return new CompanyView(c.getId(), c.getName(), c.getEmail(), c.getPhone(),
                               c.getAddress(), c.getCurrencyCode(), c.getStatus(), role);
    }
}
