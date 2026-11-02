package com.repairmanagement.repairproject.inventory;

public record DeviceCompatibilityView(String deviceType, String deviceBrand, String deviceModel) {
    public static DeviceCompatibilityView of(DeviceCompatibility c) {
        return new DeviceCompatibilityView(c.getDeviceType(), c.getDeviceBrand(), c.getDeviceModel());
    }
}
