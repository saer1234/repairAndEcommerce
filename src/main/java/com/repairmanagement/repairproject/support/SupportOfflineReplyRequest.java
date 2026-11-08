package com.repairmanagement.repairproject.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportOfflineReplyRequest(@NotBlank @Size(max = 2000) String body) {}
