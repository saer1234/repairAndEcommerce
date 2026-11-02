package com.repairmanagement.repairproject.inventory;

import jakarta.validation.constraints.NotNull;

public record UpdateItemVisibilityRequest(@NotNull Boolean visibleToClients) {}
