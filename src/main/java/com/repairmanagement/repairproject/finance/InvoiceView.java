package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceView(Long id,String invoiceNumber,InvoiceStatus status,Long customerId,Long orderId,Long repairId,
       BigDecimal subtotal,BigDecimal taxAmount,BigDecimal totalAmount,BigDecimal paidAmount,BigDecimal outstanding,
       String currencyCode,LocalDateTime issuedAt,LocalDateTime dueAt,
       String sellerName,String sellerAddress,String sellerEmail,String sellerPhone,List<InvoiceLineView> lines,List<PaymentView> payments) {}
