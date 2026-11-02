package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.NotNull;

public record AssignRepairRequest(@NotNull Long employeeId) {}
