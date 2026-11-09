package com.repairmanagement.repairproject.finance;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CompanyRevenueRangeView(
    LocalDate from, LocalDate to, String currencyCode, long completedOrders,
    long completedRepairs, BigDecimal productRevenue, BigDecimal deliveryFees,
    BigDecimal repairRevenue, BigDecimal totalRevenue) {}
