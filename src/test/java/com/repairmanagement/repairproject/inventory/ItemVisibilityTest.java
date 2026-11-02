package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemVisibilityTest {
    private Item item() {
        return new Item(new Company("Repair Store", null, null, null, "EUR"),
            "DISPLAY-1", "Phone display", null, new BigDecimal("25.00"),
            new BigDecimal("50.00"), 5);
    }

    @Test void newItemIsPubliclyVisible() {
        Item item = item();
        assertTrue(item.isActive());
        assertTrue(item.isVisibleToClients());
    }

    @Test void hidingDoesNotChangeStockOrActiveStatus() {
        Item item = item();
        item.setVisibleToClients(false);
        assertTrue(item.isActive());
        assertFalse(item.isVisibleToClients());
        assertEquals(5, item.getStockQuantity());
    }

    @Test void deletingArchivesAndRestoringKeepsItemHidden() {
        Item item = item();
        item.deactivate();
        assertFalse(item.isActive());
        assertFalse(item.isVisibleToClients());
        assertEquals(5, item.getStockQuantity());
        assertThrows(IllegalStateException.class, () -> item.setVisibleToClients(true));
        item.restore();
        assertTrue(item.isActive());
        assertFalse(item.isVisibleToClients());
        item.setVisibleToClients(true);
        assertTrue(item.isVisibleToClients());
    }
}
