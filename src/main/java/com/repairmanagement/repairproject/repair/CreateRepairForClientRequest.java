package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Staff must select a registered, role-free client account, never supply a client name. */
public record CreateRepairForClientRequest(
    @NotNull @Positive Long clientId,
    @NotBlank @Size(min = 6, max = 40) String clientPhone,
    @NotBlank @Size(max = 100) String deviceType,
    @Size(max = 100) String brand,
    @Size(max = 120) String model,
    @NotBlank @Size(max = 4000) String issueDescription
) {}
