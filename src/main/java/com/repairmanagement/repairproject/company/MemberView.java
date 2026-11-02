package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.User;

public record MemberView(Long id, Long userId, String fullName, String email, MemberRole role) {
    public static MemberView of(CompanyMember m) {
        User u = m.getUser();
        return new MemberView(m.getId(), u.getId(), u.getFullName(),
                              u.getEmail(), m.getMemberRole());
    }
}
