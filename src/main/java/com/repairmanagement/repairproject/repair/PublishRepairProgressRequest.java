package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishRepairProgressRequest(
    @NotBlank @Size(max = 2000) String customerNote
) {}
