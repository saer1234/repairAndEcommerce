package com.repairmanagement.repairproject.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCompatibilityInput(
    @NotBlank @Size(max=100) String deviceType,
    @NotBlank @Size(max=100) String deviceBrand,
    @NotBlank @Size(max=120) String deviceModel
) {}
