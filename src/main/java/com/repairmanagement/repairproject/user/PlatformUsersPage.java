package com.repairmanagement.repairproject.user;

import java.util.List;

/** Stable API response independent of Spring Data Page serialization internals. */
public record PlatformUsersPage(List<PlatformUserView> users,
        int page, int pageSize, long totalUsers, int totalPages) {}
