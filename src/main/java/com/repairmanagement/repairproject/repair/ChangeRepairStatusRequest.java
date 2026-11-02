package com.repairmanagement.repairproject.repair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeRepairStatusRequest(
    @NotNull RepairStatus status,
    @Size(max = 2000) String note,
    @Size(max = 2000) String customerNote
) {
    // Backward-compatible constructor for Stage 5 Java callers/tests.
    public ChangeRepairStatusRequest(RepairStatus status, String note) {
        this(status, note, null);
    }
}
