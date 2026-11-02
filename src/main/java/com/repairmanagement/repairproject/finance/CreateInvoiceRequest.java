package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateInvoiceRequest(Long orderId,Long repairId,
    @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal taxAmount) {}
