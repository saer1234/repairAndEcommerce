package com.repairmanagement.repairproject.inventory;

import java.math.BigDecimal;
import java.util.List;

public record ManagerItemView(Long id, String sku, String name, String description,
                              BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal effectivePrice,
                              int discountPercent, String currencyCode,
                              BigDecimal potentialGrossProfitPerUnit, int stockQuantity,
                              boolean active, boolean visibleToClients, String imageUrl, String category,
                              String deviceType, String deviceBrand, List<DeviceCompatibilityView> compatibleDevices) {
    public static ManagerItemView of(Item i) {
        return new ManagerItemView(i.getId(), i.getSku(), i.getName(), i.getDescription(),
            i.getCostPrice(), i.getSellingPrice(), i.getEffectiveSellingPrice(), i.getDiscountPercent(),
            i.getCompany().getCurrencyCode(), i.getEffectiveSellingPrice().subtract(i.getCostPrice()),
            i.getStockQuantity(), i.isActive(), i.isVisibleToClients(), i.getImageUrl(),
            i.getCategory(), i.getDeviceType(), i.getDeviceBrand(), i.getCompatibleDevices());
    }
}
