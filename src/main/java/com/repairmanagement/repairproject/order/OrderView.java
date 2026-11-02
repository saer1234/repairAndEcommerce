package com.repairmanagement.repairproject.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(Long id, String orderNumber, Long companyId, Long customerId, String currencyCode,
                        OrderStatus status, BigDecimal totalAmount, LocalDateTime orderedAt,
                        LocalDateTime completedAt, String customerName, String customerEmail,
                        DeliveryView delivery, List<OrderLineView> items) {
    public static OrderView of(CustomerOrder order, List<OrderItem> lines) {
        return new OrderView(order.getId(), order.getOrderNumber(), order.getCompany().getId(),
            order.getCustomer().getId(),
            order.getCompany().getCurrencyCode(), order.getStatus(), order.getTotalAmount(),
            order.getOrderedAt(), order.getCompletedAt(), order.getCustomerNameSnapshot(),
            order.getCustomerEmailSnapshot(), DeliveryView.from(order), lines.stream().map(OrderLineView::of).toList());
    }
}
