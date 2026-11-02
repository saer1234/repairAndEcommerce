package com.repairmanagement.repairproject.order;

import java.math.BigDecimal;

// Never expose acquisition costs on customer-facing order responses.
public record OrderLineView(Long itemId, String name, int quantity,
                            BigDecimal unitSellingPrice, BigDecimal lineTotal) {
    public static OrderLineView of(OrderItem line) {
        return new OrderLineView(line.getItem().getId(), line.getItemNameSnapshot(), line.getQuantity(),
            line.getUnitSellingPrice(),
            line.getUnitSellingPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
    }
}
