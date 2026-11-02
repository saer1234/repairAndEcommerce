package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.user.User;

/** Staff-only identity lookup; never expose passwords or company membership data. */
public record RepairClientOption(Long id, String fullName, String email, String phone) {
    public static RepairClientOption from(User user) {
        return new RepairClientOption(user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
