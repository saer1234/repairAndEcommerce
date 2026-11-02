package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemClassificationTest {
    private Item sample() {
        return new Item(new Company("Shop",null,null,null,"EUR"), "SCR-001", "Galaxy screen", null,
            new BigDecimal("25.00"), new BigDecimal("50.00"), 4, "Screen");
    }
    @Test void classifyingPhoneDoesNotOverwriteComponentCategory() {
        Item item = sample();
        item.setDeviceClassification("Phone", "Samsung", List.of(new DeviceCompatibilityInput("Phone", "Samsung", "Galaxy S24")));
        assertEquals("Screen", item.getCategory());
        assertEquals("Phone", item.getDeviceType());
        assertEquals("Samsung", item.getDeviceBrand());
    }
    @Test void screenAndBatteryAreNotDeviceTypes() {
        Item item = sample();
        assertThrows(IllegalArgumentException.class, () -> item.setDeviceClassification("Screen", "Samsung", List.of()));
        assertThrows(IllegalArgumentException.class, () -> item.setDeviceClassification("Battery", "Apple", List.of()));
        assertThrows(IllegalArgumentException.class, () -> item.setDeviceClassification("Phone", "Apple",
            List.of(new DeviceCompatibilityInput("Battery", "Apple", "iPhone 13"))));
    }
}
