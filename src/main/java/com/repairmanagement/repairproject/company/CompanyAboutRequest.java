package com.repairmanagement.repairproject.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CompanyAboutRequest(
    @Size(max=180) String headline,
    @Size(max=3000) String description,
    @Size(max=255) String storeAddress,
    @Size(max=40) String contactPhone,
    @Email @Size(max=190) String contactEmail,
    @Size(max=500) String mapsUrl,
    @Size(max=500) String instagramUrl,
    @Size(max=500) String facebookUrl,
    @Size(max=500) String tiktokUrl,
    @Size(max=500) String whatsappUrl,
    @Size(max=500) String websiteUrl,
    @Size(max=500) String openingHours
) {}
