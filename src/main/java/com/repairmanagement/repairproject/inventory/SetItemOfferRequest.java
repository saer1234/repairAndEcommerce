package com.repairmanagement.repairproject.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetItemOfferRequest(@NotNull @Min(1) @Max(90) Integer discountPercent) {}
