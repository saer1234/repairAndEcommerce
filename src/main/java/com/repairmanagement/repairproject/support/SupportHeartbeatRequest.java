package com.repairmanagement.repairproject.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SupportHeartbeatRequest(
    @NotBlank @Pattern(regexp = "[0-9a-fA-F\\-]{36}") String sessionId
) {}
