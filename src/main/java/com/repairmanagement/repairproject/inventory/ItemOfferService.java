package com.repairmanagement.repairproject.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemOfferService {
    private final ItemRepository items;
    private final InventoryAccess access;

    public ItemOfferService(ItemRepository items, InventoryAccess access) {
        this.items = items;
        this.access = access;
    }

    /** Lock the parent item row; checkout takes the same lock before reading the current offer. */
    @Transactional
    public ManagerItemView set(Long companyId, Long itemId, SetItemOfferRequest request, String email) {
        access.requireManager(companyId, email);
        Item item = items.lockForUpdate(companyId, itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (!item.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore item before offering a discount");
        if (request == null || request.discountPercent() == null || request.discountPercent() < 1 || request.discountPercent() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount must be between 1% and 90%");
        }
        try {
            item.setDiscountPercent(request.discountPercent());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        items.flush();
        return ManagerItemView.of(item);
    }

    @Transactional
    public ManagerItemView remove(Long companyId, Long itemId, String email) {
        access.requireManager(companyId, email);
        Item item = items.lockForUpdate(companyId, itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        item.clearOffer();
        items.flush();
        return ManagerItemView.of(item);
    }
}
