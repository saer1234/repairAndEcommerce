package com.repairmanagement.repairproject.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** A product may be explicitly compatible with several device families/models. */
@Embeddable
public class DeviceCompatibility {
    @Column(name = "device_type", nullable = false, length = 100)
    private String deviceType;
    @Column(name = "device_brand", nullable = false, length = 100)
    private String deviceBrand;
    @Column(name = "device_model", nullable = false, length = 120)
    private String deviceModel;

    protected DeviceCompatibility() {}
    public DeviceCompatibility(String type, String brand, String model) {
        this.deviceType = required(type, "Device type");
        this.deviceBrand = required(brand, "Brand");
        this.deviceModel = required(model, "Model");
    }
    private static String required(String s, String label) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(label + " required");
        return s.trim();
    }
    public String getDeviceType() { return deviceType; }
    public String getDeviceBrand() { return deviceBrand; }
    public String getDeviceModel() { return deviceModel; }

    private static String canonicalType(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch(v) {
            case "mobile", "mobile phone", "phone", "smart phone", "smartphone", "cellphone" -> "phone";
            case "notebook", "laptop", "laptop computer" -> "laptop";
            case "desktop", "pc", "computer" -> "computer";
            default -> v;
        };
    }
    private static boolean equalOrWildcard(String configured, String actual) {
        return "*".equals(configured) || (actual != null && configured.equalsIgnoreCase(actual.trim()));
    }
    public boolean matches(String type, String brand, String model) {
        return ("*".equals(deviceType) || canonicalType(deviceType).equals(canonicalType(type)))
            && equalOrWildcard(deviceBrand, brand)
            && equalOrWildcard(deviceModel, model);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeviceCompatibility d)) return false;
        return deviceType.equalsIgnoreCase(d.deviceType) && deviceBrand.equalsIgnoreCase(d.deviceBrand)
            && deviceModel.equalsIgnoreCase(d.deviceModel);
    }
    @Override public int hashCode() {
        return Objects.hash(deviceType.toLowerCase(java.util.Locale.ROOT),
            deviceBrand.toLowerCase(java.util.Locale.ROOT), deviceModel.toLowerCase(java.util.Locale.ROOT));
    }
}
