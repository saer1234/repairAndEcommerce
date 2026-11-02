package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddRepairPartRequest(
    @NotNull Long itemId,
    @Min(1) int quantity,
    boolean confirmUnverifiedCompatibility
) {
    public AddRepairPartRequest(Long itemId, int quantity) {
        this(itemId, quantity, false);
    }
}
