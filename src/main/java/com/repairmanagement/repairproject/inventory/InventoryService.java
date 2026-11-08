package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final ItemRepository items;
    private final StockMovementRepository movements;
    private final InventoryAccess access;

    public InventoryService(ItemRepository items, StockMovementRepository movements, InventoryAccess access) {
        this.items = items;
        this.movements = movements;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<StockItemView> stock(Long companyId, String email) {
        access.requireMember(companyId, email);
        return items.findByCompany_IdOrderByNameAsc(companyId).stream().map(StockItemView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ManagerItemView> managerItems(Long companyId, String email) {
        access.requireManager(companyId, email);
        return items.findByCompany_IdOrderByNameAsc(companyId).stream().map(ManagerItemView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementView> history(Long companyId, Long itemId, String email) {
        access.requireMember(companyId, email);
        items.findByIdAndCompany_Id(itemId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        return movements.findByItem_IdOrderByCreatedAtDesc(itemId)
            .stream().map(StockMovementView::of).toList();
    }

    @Transactional
    public StockItemView adjust(Long companyId, Long itemId, StockAdjustmentRequest request, String email) {
        // Technicians consume/return parts only through their assigned repair, not via global adjustments.
        User actor = access.requireManager(companyId, email);
        if (request.movementType() != MovementType.RESTOCK && request.movementType() != MovementType.ADJUSTMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only RESTOCK and ADJUSTMENT are allowed manually; sales and repairs need their own workflow");
        }
        int delta = request.quantityChange();
        if (delta == 0 || (request.movementType() == MovementType.RESTOCK && delta < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stock quantity change");
        }
        if (request.movementType() == MovementType.ADJUSTMENT &&
            (request.note() == null || request.note().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjustment reason is required");
        }
        Item item = items.lockForUpdate(companyId, itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (!item.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is deactivated");
        }
        long next = (long) item.getStockQuantity() + delta;
        if (next < 0 || next > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock or quantity overflow");
        }
        item.setStockQuantity((int) next);
        movements.save(new StockMovement(item, actor, request.movementType(), delta,
            request.note() == null ? null : request.note().trim()));
        return StockItemView.of(item);
    }

    @Transactional(readOnly = true)
    public InventorySummaryView summary(Long companyId, String email) {
        access.requireManager(companyId, email);
        Company company = access.requireActiveCompany(companyId);
        List<Item> inventory = items.findByCompany_IdOrderByNameAsc(companyId);
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        long units = 0L;
        int activeItems = 0;
        for (Item item : inventory) {
            if (!item.isActive()) continue; // Archived items are not saleable inventory.
            activeItems++;
            units += item.getStockQuantity();
            BigDecimal quantity = BigDecimal.valueOf(item.getStockQuantity());
            totalCost = totalCost.add(item.getCostPrice().multiply(quantity));
            totalSales = totalSales.add(item.getEffectiveSellingPrice().multiply(quantity));
        }
        return new InventorySummaryView(companyId, company.getCurrencyCode(), activeItems, units,
            totalCost, totalSales, totalSales.subtract(totalCost));
    }
}
