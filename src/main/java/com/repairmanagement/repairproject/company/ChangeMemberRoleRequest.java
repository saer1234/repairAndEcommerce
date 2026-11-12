package com.repairmanagement.repairproject.company;

import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull MemberRole role) {}
