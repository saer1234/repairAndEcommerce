package com.repairmanagement.repairproject.finance;
import java.math.BigDecimal;
import java.util.List;
/** Manager/owner-only. Gross profit excludes tax and operating costs. */
public record RepairInvoiceInternalReport(RepairInvoiceReport invoice,
    BigDecimal laborCost, BigDecimal partsCost, BigDecimal totalDirectCost,
    BigDecimal grossProfitBeforeTax, List<RepairInvoiceInternalLine> directCostLines) {}
