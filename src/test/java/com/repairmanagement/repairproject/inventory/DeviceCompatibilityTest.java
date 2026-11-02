package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceCompatibilityTest {
    @Test void oneScreenSupportsSeveralPhonesAndBrand() {
        Item item = new Item(new Company("Shop",null,null,null,"EUR"), "SCR-1", "Screen", null,
            new BigDecimal("20"), new BigDecimal("50"), 5, "Screen");
        item.setDeviceClassification("Phone", "Samsung", List.of(
            new DeviceCompatibilityInput("Phone", "Samsung", "Galaxy S23"),
            new DeviceCompatibilityInput("Phone", "Samsung", "Galaxy S24"),
            new DeviceCompatibilityInput("Phone", "Xiaomi", "Redmi Note 12")
        ));
        assertTrue(item.supportsDevice("mobile phone", "samsung", "Galaxy S23"));
        assertTrue(item.supportsDevice("Phone", "Samsung", "Galaxy S24"));
        assertTrue(item.supportsDevice("Phone", "Xiaomi", "Redmi Note 12"));
        assertFalse(item.supportsDevice("Phone", "Apple", "iPhone 15"));
        assertFalse(item.supportsDevice("Tablet", "Samsung", "Galaxy S24"));
        assertEquals(3, item.getCompatibleDevices().size());
        assertEquals("Phone", item.getDeviceType());
        assertEquals("Screen", item.getCategory());
    }

    @Test void explicitWildcardModelAndEmptyListAreDifferent() {
        Item item = new Item(new Company("Shop",null,null,null,"EUR"), "CABLE", "Cable", null,
            BigDecimal.ONE, BigDecimal.TEN, 2);
        assertTrue(item.compatibilityUnspecified());
        assertFalse(item.supportsDevice("Phone", "Apple", "iPhone 16"));
        item.setDeviceClassification("Phone", "Universal", List.of(
            new DeviceCompatibilityInput("Phone", "Apple", "*")));
        assertFalse(item.compatibilityUnspecified());
        assertTrue(item.supportsDevice("Smartphone", "Apple", "iPhone 16"));
        assertFalse(item.supportsDevice("Phone", "Samsung", "Galaxy S24"));
    }
}
