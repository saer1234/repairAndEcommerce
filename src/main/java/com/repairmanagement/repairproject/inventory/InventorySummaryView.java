package com.repairmanagement.repairproject.inventory;

import java.math.BigDecimal;

// These values are hypothetical valuation at current list prices, NOT realized earnings.
public record InventorySummaryView(Long companyId, String currencyCode, int totalSkus,
                                   long unitsOnHand, BigDecimal inventoryCostValue,
                                   BigDecimal potentialSalesValue, BigDecimal potentialGrossProfit) {}
