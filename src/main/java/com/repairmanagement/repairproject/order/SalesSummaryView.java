package com.repairmanagement.repairproject.order;

import java.math.BigDecimal;

// Gross profit from manually marked COMPLETED orders. No verified payments, tax or platform fees yet.
public record SalesSummaryView(Long companyId, String currencyCode, long completedOrders,
                               BigDecimal completedSalesRevenue, BigDecimal costOfGoodsSold,
                               BigDecimal completedGrossProfit) {}
