package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecordPaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
    @NotNull PaymentMethod method,@NotBlank @Size(max=190) String reference) {}
