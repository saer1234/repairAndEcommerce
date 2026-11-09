package com.repairmanagement.repairproject.order;
import java.math.BigDecimal;
public record DeliverySettingsView(boolean deliveryEnabled, BigDecimal deliveryFee, String currencyCode) {}
