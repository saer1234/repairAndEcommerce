package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {
    private final ItemRepository items;
    private final StockMovementRepository movements;
    private final InventoryAccess access;

    public ItemService(ItemRepository items, StockMovementRepository movements, InventoryAccess access) {
        this.items = items;
        this.movements = movements;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<PublicItemView> browse(Long companyId) {
        access.requireActiveCompany(companyId);
        return items.findByCompany_IdAndActiveTrueAndVisibleToClientsTrueOrderByNameAsc(companyId)
            .stream().map(PublicItemView::of).toList();
    }

    @Transactional(readOnly = true)
    public PublicItemView detail(Long companyId, Long itemId) {
        access.requireActiveCompany(companyId);
        Item item = items.findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(itemId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        return PublicItemView.of(item);
    }

    @Transactional
    public ManagerItemView create(Long companyId, CreateItemRequest request, String email) {
        User actor = access.requireManager(companyId, email);
        Company company = access.requireActiveCompany(companyId);
        String sku = normalizeSku(request.sku());
        if (items.existsByCompany_IdAndSkuIgnoreCase(companyId, sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already used by this company");
        }
        Item item = new Item(company, sku, request.name().trim(), request.description(),
            request.costPrice(), request.sellingPrice(), request.openingStock(), request.category());
        item.setDeviceClassification(request.deviceType(), request.deviceBrand(), request.compatibleDevices());
        try {
            items.saveAndFlush(item);
            if (request.openingStock() > 0) {
                movements.save(new StockMovement(item, actor, MovementType.RESTOCK,
                    request.openingStock(), "Opening inventory"));
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item violates a unique or database constraint");
        }
        return ManagerItemView.of(item);
    }

    @Transactional
    public ManagerItemView update(Long companyId, Long itemId, UpdateItemRequest request, String email) {
        access.requireManager(companyId, email);
        Item item = requireItem(companyId, itemId);
        if (!item.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot edit a deactivated item");
        }
        String sku = normalizeSku(request.sku());
        if (items.existsByCompany_IdAndSkuIgnoreCaseAndIdNot(companyId, sku, itemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already used by this company");
        }
        if (item.getDiscountPercent() > 0 &&
            request.sellingPrice().multiply(java.math.BigDecimal.valueOf(100 - item.getDiscountPercent()))
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP).signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Remove or adjust the offer before setting a list price that makes the offer price zero");
        }
        item.updateDetails(sku, request.name().trim(), request.description(),
            request.costPrice(), request.sellingPrice());
        item.setCategory(request.category());
        item.setDeviceClassification(request.deviceType(), request.deviceBrand(), request.compatibleDevices());
        try {
            items.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item violates a unique or database constraint");
        }
        return ManagerItemView.of(item);
    }

    @Transactional
    public ManagerItemView setVisibility(Long companyId, Long itemId, boolean visible, String email) {
        access.requireManager(companyId, email);
        Item item = lockItem(companyId, itemId);
        if (!item.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore the deleted item before changing visibility");
        }
        item.setVisibleToClients(visible);
        return ManagerItemView.of(item);
    }

    @Transactional
    public void deactivate(Long companyId, Long itemId, String email) {
        access.requireManager(companyId, email);
        Item item = lockItem(companyId, itemId);
        item.deactivate();  // archival delete: keeps referenced orders, repairs and stock history
    }

    @Transactional
    public ManagerItemView restore(Long companyId, Long itemId, String email) {
        access.requireManager(companyId, email);
        Item item = lockItem(companyId, itemId);
        if (!item.isActive()) item.restore();  // restored but hidden until deliberately published
        return ManagerItemView.of(item);
    }

    private Item lockItem(Long companyId, Long itemId) {
        return items.lockForUpdate(companyId, itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Item requireItem(Long companyId, Long itemId) {
        return items.findByIdAndCompany_Id(itemId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private static String normalizeSku(String sku) { return sku.trim().toUpperCase(Locale.ROOT); }
}
