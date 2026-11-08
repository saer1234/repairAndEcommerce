package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.order.CustomerOrder;
import com.repairmanagement.repairproject.order.OrderItem;
import com.repairmanagement.repairproject.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemOfferTest {
    private Item item() {
        return new Item(new Company("Demo Shop", null, null, null, "USD"),
            "BAT-01", "Battery", null, new BigDecimal("50.00"),
            new BigDecimal("120.00"), 3);
    }

    @Test void discountDoesNotOverwriteListPrice() {
        Item i = item();
        i.setDiscountPercent(25);
        assertEquals(new BigDecimal("120.00"), i.getSellingPrice());
        assertEquals(new BigDecimal("90.00"), i.getEffectiveSellingPrice());
        assertEquals(25, i.getDiscountPercent());
        i.clearOffer();
        assertEquals(new BigDecimal("120.00"), i.getEffectiveSellingPrice());
    }

    @Test void checkoutLineCapturesDiscountedPriceEvenIfOfferChanges() {
        Item i = item();
        i.setDiscountPercent(20);
        User client = new User("Test", "test@example.com", "hash", null);
        CustomerOrder order = new CustomerOrder(i.getCompany(), client, "ORDER-1", new BigDecimal("192.00"));
        OrderItem line = new OrderItem(order, i, 2);
        i.clearOffer();
        assertEquals(new BigDecimal("96.00"), line.getUnitSellingPrice());
        assertEquals(new BigDecimal("50.00"), line.getUnitCostPrice());
        assertEquals(new BigDecimal("120.00"), i.getEffectiveSellingPrice());
    }

    @Test void rejectsInvalidDiscountsAndZeroPrice() {
        Item i = item();
        assertThrows(IllegalArgumentException.class, () -> i.setDiscountPercent(0));
        assertThrows(IllegalArgumentException.class, () -> i.setDiscountPercent(91));
        i.updateDetails("BAT-01", "Battery", null, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () -> i.setDiscountPercent(10));
    }

    @Test void cartShowsSamePriceAsCheckout() {
        Item i = item();
        i.setDiscountPercent(15);
        assertEquals(new BigDecimal("102.00"), i.getEffectiveSellingPrice());
    }
}
