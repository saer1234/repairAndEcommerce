package com.repairmanagement.repairproject.order;

import java.math.BigDecimal;
import java.util.List;

// Internal manager-only cost report; NEVER embed in an OrderView or customer invoice.
public record OrderProfitView(Long orderId, String currencyCode, BigDecimal revenue,
    BigDecimal acquisitionCost, BigDecimal grossProfit, List<CostLine> lines) {
    public record CostLine(String name, int quantity, BigDecimal unitSellingPrice,
        BigDecimal unitCostPrice, BigDecimal lineRevenue, BigDecimal lineCost, BigDecimal lineGrossProfit) {
        static CostLine of(OrderItem i) {
            BigDecimal quantity = BigDecimal.valueOf(i.getQuantity());
            BigDecimal sales = i.getUnitSellingPrice().multiply(quantity);
            BigDecimal cost = i.getUnitCostPrice().multiply(quantity);
            return new CostLine(i.getItemNameSnapshot(), i.getQuantity(), i.getUnitSellingPrice(),
                i.getUnitCostPrice(), sales, cost, sales.subtract(cost));
        }
    }
    public static OrderProfitView of(CustomerOrder order, List<OrderItem> items) {
        List<CostLine> lines = items.stream().map(CostLine::of).toList();
        BigDecimal revenue = lines.stream().map(CostLine::lineRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = lines.stream().map(CostLine::lineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderProfitView(order.getId(), order.getCompany().getCurrencyCode(),
            revenue, cost, revenue.subtract(cost), lines);
    }
}
