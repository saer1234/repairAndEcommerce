package com.repairmanagement.repairproject.repair;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairUpdateView(
    Long id, Long updatedByUserId, RepairStatus status,
    String note, String customerNote, LocalDateTime createdAt
) {
    public static RepairUpdateView of(RepairUpdate update, boolean staffView) {
        // Staff see private notes + published messages. Customers only see published text.
        return new RepairUpdateView(update.getId(),
            staffView ? update.getUpdatedBy().getId() : null,
            update.getNewStatus(),
            staffView ? update.getNote() : update.getCustomerNote(),
            staffView ? update.getCustomerNote() : null,
            update.getCreatedAt());
    }
}
