package com.repairmanagement.repairproject.company;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "company_about_profiles")
public class CompanyAboutProfile {
    @Id private Long companyId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(length = 180) private String headline;
    @Column(length = 3000) private String description;
    @Column(name = "store_address", length = 255) private String storeAddress;
    @Column(name = "contact_phone", length = 40) private String contactPhone;
    @Column(name = "contact_email", length = 190) private String contactEmail;
    @Column(name = "maps_url", length = 500) private String mapsUrl;
    @Column(name = "instagram_url", length = 500) private String instagramUrl;
    @Column(name = "facebook_url", length = 500) private String facebookUrl;
    @Column(name = "tiktok_url", length = 500) private String tiktokUrl;
    @Column(name = "whatsapp_url", length = 500) private String whatsappUrl;
    @Column(name = "website_url", length = 500) private String websiteUrl;
    @Column(name = "opening_hours", length = 500) private String openingHours;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected CompanyAboutProfile() {}
    public CompanyAboutProfile(Company company) { this.company = company; }

    public void apply(CompanyAboutRequest v) {
        headline = trimmed(v.headline()); description = trimmed(v.description());
        storeAddress = trimmed(v.storeAddress()); contactPhone = trimmed(v.contactPhone());
        contactEmail = trimmed(v.contactEmail()); mapsUrl = trimmed(v.mapsUrl());
        instagramUrl = trimmed(v.instagramUrl()); facebookUrl = trimmed(v.facebookUrl());
        tiktokUrl = trimmed(v.tiktokUrl()); whatsappUrl = trimmed(v.whatsappUrl());
        websiteUrl = trimmed(v.websiteUrl()); openingHours = trimmed(v.openingHours());
    }
    private String trimmed(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public String getHeadline() { return headline; }
    public String getDescription() { return description; }
    public String getStoreAddress() { return storeAddress; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public String getMapsUrl() { return mapsUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getTiktokUrl() { return tiktokUrl; }
    public String getWhatsappUrl() { return whatsappUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getOpeningHours() { return openingHours; }
}
