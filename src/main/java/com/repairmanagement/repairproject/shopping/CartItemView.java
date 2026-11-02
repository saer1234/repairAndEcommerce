package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.inventory.Item;
import java.math.BigDecimal;

public record CartItemView(Long itemId, String sku, String name, int quantity,
                           BigDecimal unitSellingPrice, BigDecimal lineTotal) {
    public static CartItemView of(CartItem entry) {
        Item item = entry.getItem();
        return new CartItemView(item.getId(), item.getSku(), item.getName(), entry.getQuantity(),
            item.getSellingPrice(), item.getSellingPrice().multiply(BigDecimal.valueOf(entry.getQuantity())));
    }
}
