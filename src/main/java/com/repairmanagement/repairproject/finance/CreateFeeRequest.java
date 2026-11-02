package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateFeeRequest(@NotNull Long companyId,Long orderId,Long repairId,
    @NotNull FeeType feeType,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
