package com.repairmanagement.repairproject.support;

import jakarta.validation.constraints.NotNull;

public record SupportStatusRequest(@NotNull SupportStatus status) {}
