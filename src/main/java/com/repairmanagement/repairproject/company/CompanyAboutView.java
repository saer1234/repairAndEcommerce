package com.repairmanagement.repairproject.company;

public record CompanyAboutView(
    Long companyId, String companyName, String headline, String description,
    String storeAddress, String contactPhone, String contactEmail,
    String mapsUrl, String instagramUrl, String facebookUrl, String tiktokUrl,
    String whatsappUrl, String websiteUrl, String openingHours
) {
    public static CompanyAboutView of(Company c, CompanyAboutProfile p) {
        return new CompanyAboutView(c.getId(), c.getName(),
            p == null || p.getHeadline() == null ? "About " + c.getName() : p.getHeadline(),
            p == null ? null : p.getDescription(),
            p == null || p.getStoreAddress() == null ? c.getAddress() : p.getStoreAddress(),
            p == null || p.getContactPhone() == null ? c.getPhone() : p.getContactPhone(),
            p == null || p.getContactEmail() == null ? c.getEmail() : p.getContactEmail(),
            p == null ? null : p.getMapsUrl(), p == null ? null : p.getInstagramUrl(),
            p == null ? null : p.getFacebookUrl(), p == null ? null : p.getTiktokUrl(),
            p == null ? null : p.getWhatsappUrl(), p == null ? null : p.getWebsiteUrl(),
            p == null ? null : p.getOpeningHours());
    }
}
