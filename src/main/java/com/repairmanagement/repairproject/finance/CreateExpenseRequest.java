package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateExpenseRequest(@NotBlank @Size(max=80) String category,@Size(max=255) String description,
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
    @NotNull LocalDate expenseDate) {}
