package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.Item;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartItemViewTest {
    @Test
    void viewContainsOnlyCustomerFacingPrice() {
        Item item = new Item(new Company("Shop", null, null, null, "EUR"), "SKU", "Screen", null,
            new BigDecimal("25.00"), new BigDecimal("40.00"), 10);
        CartItem line = new CartItem(null, item, 2);
        CartItemView view = CartItemView.of(line);
        assertEquals(new BigDecimal("80.00"), view.lineTotal());
        assertEquals(new BigDecimal("40.00"), view.unitSellingPrice());
        assertFalse(java.util.Arrays.stream(view.getClass().getRecordComponents())
            .anyMatch(c -> c.getName().toLowerCase().contains("cost")));
    }
}
