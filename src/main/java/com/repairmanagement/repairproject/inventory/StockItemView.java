package com.repairmanagement.repairproject.inventory;

import java.util.List;

// Visible to employees; no purchase-cost or profit information.
public record StockItemView(Long id, String sku, String name, String category, int stockQuantity, boolean active, String deviceType, String deviceBrand, List<DeviceCompatibilityView> compatibleDevices) {
    public static StockItemView of(Item i) {
        return new StockItemView(i.getId(), i.getSku(), i.getName(), i.getCategory(), i.getStockQuantity(), i.isActive(), i.getDeviceType(), i.getDeviceBrand(), i.getCompatibleDevices());
    }
}
