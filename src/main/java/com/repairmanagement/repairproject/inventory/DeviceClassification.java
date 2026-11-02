package com.repairmanagement.repairproject.inventory;

import java.util.Locale;
import java.util.Set;

/** Device type (Phone) is not a component category (Screen/Battery). */
public final class DeviceClassification {
    private DeviceClassification() {}
    private static final Set<String> PART_CATEGORIES = Set.of(
        "screen", "display", "battery", "charging port", "charging cable",
        "charging flex", "speaker", "microphone", "back cover", "rear cover",
        "housing", "motherboard", "flex cable", "camera module", "camera lens",
        "charging connector", "power button", "vibration motor", "sim tray", "antenna"
    );
    public static void requireDeviceType(String type, boolean allowWildcard) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (allowWildcard && normalized.equals("*")) return;
        if (normalized.isEmpty() || normalized.equals("general") || normalized.equals("*")
                || normalized.equals("uncategorized") || PART_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid device type '" + type
                + "'. Choose a device such as Phone, Tablet, or Laptop. Put Screen/Battery in component category.");
        }
    }
}
