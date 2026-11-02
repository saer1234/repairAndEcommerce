package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FeeView(Long id,FeeType type,BigDecimal amount,String currencyCode,FeeStatus status,LocalDateTime earnedAt){
    static FeeView from(PlatformFee f){return new FeeView(f.getId(),f.getFeeType(),f.getFeeAmount(),f.getCurrencyCode(),f.getStatus(),f.getEarnedAt());}
}
