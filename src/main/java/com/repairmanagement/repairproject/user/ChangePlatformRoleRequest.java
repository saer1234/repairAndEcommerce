package com.repairmanagement.repairproject.user;

import jakarta.validation.constraints.NotNull;

public record ChangePlatformRoleRequest(@NotNull PlatformRole role) {}
