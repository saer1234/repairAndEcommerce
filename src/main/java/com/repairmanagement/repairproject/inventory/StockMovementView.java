package com.repairmanagement.repairproject.inventory;

import java.time.LocalDateTime;

public record StockMovementView(Long id, Long itemId, Long performedByUserId,
                                MovementType movementType, int quantityChange, String note,
                                LocalDateTime createdAt) {
    public static StockMovementView of(StockMovement movement) {
        return new StockMovementView(movement.getId(), movement.getItem().getId(),
            movement.getPerformedBy() == null ? null : movement.getPerformedBy().getId(),
            movement.getMovementType(), movement.getQuantityChange(), movement.getNote(),
            movement.getCreatedAt());
    }
}
