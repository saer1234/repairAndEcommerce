package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentView(Long id,BigDecimal amount,PaymentMethod method,PaymentStatus status,String reference,LocalDateTime paidAt){
    static PaymentView from(Payment p){return new PaymentView(p.getId(),p.getAmount(),p.getPaymentMethod(),
        p.getStatus(),p.getProviderReference(),p.getPaidAt());}
}
