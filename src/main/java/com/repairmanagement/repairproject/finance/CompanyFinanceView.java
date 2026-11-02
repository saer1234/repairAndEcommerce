package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CompanyFinanceView(Long companyId,String currencyCode,BigDecimal completedSalesRevenue,
    BigDecimal completedRepairRevenue,BigDecimal directCosts,BigDecimal grossProfit,
    BigDecimal operatingExpenses,BigDecimal platformFeesEarned,BigDecimal estimatedOperatingResultBeforeTax,
    BigDecimal cashCollectedIncludingTax,BigDecimal taxOnCollectedInvoices,
    BigDecimal unpaidIssuedInvoices) {}
