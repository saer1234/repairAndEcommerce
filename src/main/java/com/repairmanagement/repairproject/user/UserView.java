package com.repairmanagement.repairproject.user;

public record UserView(Long id, String fullName, String email, String phone, PlatformRole platformRole) {
    public static UserView of(User user) {
        return new UserView(user.getId(), user.getFullName(), user.getEmail(),
                            user.getPhone(), user.getPlatformRole());
    }
}
