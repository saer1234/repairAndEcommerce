package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRepairRequest(
    // The client account and full name come exclusively from the authenticated principal.
    @NotBlank @Size(min = 6, max = 40) String clientPhone,
    @NotBlank @Size(max = 100) String deviceType,
    @Size(max = 100) String brand,
    @Size(max = 120) String model,
    @NotBlank @Size(max = 4000) String issueDescription
) {}
