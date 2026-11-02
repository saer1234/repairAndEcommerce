package com.repairmanagement.repairproject.inventory;

import java.math.BigDecimal;
import java.util.List;

// No cost price or gross margin leaks to customers.
public record PublicItemView(Long id, String sku, String name, String description,
                             BigDecimal sellingPrice, String currencyCode, boolean inStock, String imageUrl, String category, String deviceType, String deviceBrand, List<DeviceCompatibilityView> compatibleDevices) {
    public static PublicItemView of(Item i) {
        return new PublicItemView(i.getId(), i.getSku(), i.getName(), i.getDescription(),
            i.getSellingPrice(), i.getCompany().getCurrencyCode(), i.getStockQuantity() > 0, i.getImageUrl(), i.getCategory(), i.getDeviceType(), i.getDeviceBrand(), i.getCompatibleDevices());
    }
}
