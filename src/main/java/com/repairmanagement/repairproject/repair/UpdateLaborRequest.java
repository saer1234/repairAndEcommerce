package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateLaborRequest(
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborCharge,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborCost
) {}
