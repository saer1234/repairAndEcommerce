package com.repairmanagement.repairproject.repair;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairPartView(
    Long id, Long itemId, String itemName, int quantity,
    BigDecimal unitSellingPrice, BigDecimal unitCostPrice
) {
    public static RepairPartView of(RepairPart part, boolean canSeeCosts) {
        return new RepairPartView(part.getId(), part.getItem().getId(),
            part.getItem().getName(), part.getQuantity(), part.getUnitSellingPrice(),
            canSeeCosts ? part.getUnitCostPrice() : null);
    }
}
