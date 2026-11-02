package com.repairmanagement.repairproject.inventory;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

// Stock cannot be changed via PUT; use an audited stock movement endpoint instead.
public record UpdateItemRequest(
    @NotBlank @Size(max = 90) String sku,
    @NotBlank @Size(max = 180) String name,
    String description,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
    @Size(max = 80) String category,
    @NotBlank @Size(max = 100) String deviceType,
    @NotBlank @Size(max = 100) String deviceBrand,
    @Size(max = 50) List<@Valid DeviceCompatibilityInput> compatibleDevices
) {
    public UpdateItemRequest(String sku, String name, String description,
                             BigDecimal costPrice, BigDecimal sellingPrice, String category) {
        this(sku, name, description, costPrice, sellingPrice, category, category, "Universal", List.of());
    }
    public UpdateItemRequest(String sku, String name, String description,
                             BigDecimal costPrice, BigDecimal sellingPrice) {
        this(sku, name, description, costPrice, sellingPrice, "General", "General", "Universal", List.of());
    }
}
