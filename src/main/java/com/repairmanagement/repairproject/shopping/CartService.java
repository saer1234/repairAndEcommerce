package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.inventory.Item;
import com.repairmanagement.repairproject.inventory.ItemRepository;
import com.repairmanagement.repairproject.user.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {
    private final ShoppingCartRepository carts;
    private final CartItemRepository lines;
    private final ItemRepository items;
    private final ShoppingEligibility shoppers;
    private final InventoryAccess access;

    public CartService(ShoppingCartRepository carts, CartItemRepository lines,
                       ItemRepository items, ShoppingEligibility shoppers, InventoryAccess access) {
        this.carts = carts;
        this.lines = lines;
        this.items = items;
        this.shoppers = shoppers;
        this.access = access;
    }

    @Transactional
    public CartView get(Long companyId, String email) {
        User user = shoppers.requireClient(email);
        Company company = access.requireActiveCompany(companyId);
        ShoppingCart cart = lockOrCreate(company, user);
        return toView(company, cart);
    }

    // PUT assigns a quantity (not an increment), allowing easy client retries.
    @Transactional
    public CartView setQuantity(Long companyId, Long itemId, int quantity, String email) {
        if (quantity <= 0 || quantity > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be between 1 and 100000");
        }
        User user = shoppers.requireClient(email);
        Company company = access.requireActiveCompany(companyId);
        ShoppingCart cart = lockOrCreate(company, user);
        Item item = items.findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(itemId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (quantity > item.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock");
        }
        CartItem line = lines.findByCart_IdAndItem_Id(cart.getId(), itemId)
            .orElseGet(() -> new CartItem(cart, item, quantity));
        line.setQuantity(quantity);
        lines.saveAndFlush(line);
        return toView(company, cart);
    }

    @Transactional
    public CartView remove(Long companyId, Long itemId, String email) {
        User user = shoppers.requireClient(email);
        Company company = access.requireActiveCompany(companyId);
        ShoppingCart cart = lockOrCreate(company, user);
        lines.findByCart_IdAndItem_Id(cart.getId(), itemId).ifPresent(lines::delete);
        lines.flush();
        return toView(company, cart);
    }

    private ShoppingCart lockOrCreate(Company company, User user) {
        return carts.lockForUpdate(company.getId(), user.getId()).orElseGet(() ->
            carts.saveAndFlush(new ShoppingCart(company, user)));
    }

    private CartView toView(Company company, ShoppingCart cart) {
        List<CartItem> contents = lines.findByCart_IdOrderByIdAsc(cart.getId());
        return CartView.of(company.getId(), company.getCurrencyCode(), contents);
    }
}
