package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePlatformExpenseRequest(
    @NotBlank @Size(max=80) String category,
    @Size(max=255) String description,
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
    @NotBlank @Pattern(regexp="[A-Z]{3}") String currencyCode,
    @NotNull LocalDate expenseDate
) {}
