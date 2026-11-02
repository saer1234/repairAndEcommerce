package com.repairmanagement.repairproject.repair;

import java.math.BigDecimal;

public record RepairProfitSummaryView(
    Long companyId, String currencyCode, int completedOrDeliveredRepairs,
    BigDecimal laborRevenue, BigDecimal partsRevenue,
    BigDecimal laborCost, BigDecimal partsCost,
    BigDecimal estimatedGrossProfit
) {}
