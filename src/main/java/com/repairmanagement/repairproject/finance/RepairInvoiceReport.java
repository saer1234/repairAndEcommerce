package com.repairmanagement.repairproject.finance;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/** Safe to return to the named client; intentionally excludes purchase and labor costs. */
public record RepairInvoiceReport(
    Long invoiceId, String invoiceNumber, InvoiceStatus status,
    Long repairId, String device, String reportedProblem,
    String diagnosis, String workPerformed, String technicianName,
    String customerName, String customerPhone,
    String companyName, String companyAddress, String companyEmail, String companyPhone,
    String currencyCode, LocalDateTime issuedAt, LocalDateTime dueAt,
    BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount,
    List<InvoiceLineView> items
) {}
