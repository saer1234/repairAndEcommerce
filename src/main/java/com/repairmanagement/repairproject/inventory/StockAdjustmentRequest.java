package com.repairmanagement.repairproject.inventory;

import jakarta.validation.constraints.*;

public record StockAdjustmentRequest(
    @NotNull MovementType movementType,
    @NotNull Integer quantityChange,
    @Size(max = 255) String note
) {}
