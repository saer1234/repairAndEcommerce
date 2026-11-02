package com.repairmanagement.repairproject.repair;

import java.time.LocalDateTime;

public record CustomerRepairEventView(
    Long id, RepairStatus status, String note, LocalDateTime createdAt
) {
    static CustomerRepairEventView from(RepairUpdate update) {
        return new CustomerRepairEventView(update.getId(), update.getNewStatus(),
            update.getCustomerNote(), update.getCreatedAt());
    }
}
