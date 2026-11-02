package com.repairmanagement.repairproject.shopping;

import java.math.BigDecimal;
import java.util.List;

public record CartView(Long companyId, String currencyCode, List<CartItemView> items,
                       BigDecimal estimatedTotal) {
    public static CartView of(Long companyId, String currency, List<CartItem> cartItems) {
        List<CartItemView> lines = cartItems.stream().map(CartItemView::of).toList();
        BigDecimal sum = lines.stream().map(CartItemView::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(companyId, currency, lines, sum);
    }
}
