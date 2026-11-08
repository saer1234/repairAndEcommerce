package com.repairmanagement.repairproject.user;

import java.time.LocalDateTime;

/** Public-facing data restricted to the platform administrator endpoint. */
public record PlatformUserView(
        Long id, String fullName, String email, String phone,
        PlatformRole platformRole, boolean active, LocalDateTime createdAt) {
    public static PlatformUserView of(User user) {
        return new PlatformUserView(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getPlatformRole(), user.isActive(), user.getCreatedAt());
    }
}
