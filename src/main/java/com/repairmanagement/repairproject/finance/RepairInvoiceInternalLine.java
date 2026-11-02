package com.repairmanagement.repairproject.finance;
import java.math.BigDecimal;
public record RepairInvoiceInternalLine(String description, int quantity,
    BigDecimal unitCost, BigDecimal totalCost) {}
