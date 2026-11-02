package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemViewTest {
    @Test
    void publicCatalogDoesNotExposeCostPrice() {
        Item item = new Item(new Company("Shop", null, null, null, "EUR"),
            "SCREEN-001", "Screen", null, new BigDecimal("50.00"),
            new BigDecimal("80.00"), 1);
        PublicItemView publicView = PublicItemView.of(item);
        assertEquals(new BigDecimal("80.00"), publicView.sellingPrice());
        assertEquals("EUR", publicView.currencyCode());
        assertTrue(publicView.inStock());
        assertTrue(java.util.Arrays.stream(PublicItemView.class.getRecordComponents())
            .noneMatch(c -> c.getName().equals("costPrice")));
    }
}
