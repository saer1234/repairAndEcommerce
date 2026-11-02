package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.inventory.Item;
import com.repairmanagement.repairproject.inventory.ItemRepository;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {
    private ShoppingCartRepository carts;
    private CartItemRepository lines;
    private ItemRepository items;
    private ShoppingEligibility shoppers;
    private InventoryAccess access;
    private CartService service;
    private ShoppingCart cart;
    private Item item;

    @BeforeEach
    void setup() {
        carts = mock(ShoppingCartRepository.class);
        lines = mock(CartItemRepository.class);
        items = mock(ItemRepository.class);
        shoppers = mock(ShoppingEligibility.class);
        access = mock(InventoryAccess.class);
        service = new CartService(carts, lines, items, shoppers, access);
        Company company = new Company("Store", null, null, null, "EUR");
        ReflectionTestUtils.setField(company, "id", 1L);
        User customer = new User("Sam", "sam@example.com", "hash", null);
        ReflectionTestUtils.setField(customer, "id", 5L);
        cart = new ShoppingCart(company, customer);
        ReflectionTestUtils.setField(cart, "id", 10L);
        item = new Item(company, "SKU-1", "Part", null,
            new BigDecimal("12.00"), new BigDecimal("20.00"), 4);
        ReflectionTestUtils.setField(item, "id", 9L);
        when(shoppers.requireClient("sam@example.com")).thenReturn(customer);
        when(access.requireActiveCompany(1L)).thenReturn(company);
        when(carts.lockForUpdate(1L, 5L)).thenReturn(Optional.of(cart));
    }

    @Test
    void quantityBeyondAvailableStockFails() {
        when(items.findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(9L, 1L)).thenReturn(Optional.of(item));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.setQuantity(1L, 9L, 5, "sam@example.com"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(lines, never()).saveAndFlush(any());
    }

    @Test
    void itemFromAnotherCompanyCannotBeAdded() {
        when(items.findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(9L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.setQuantity(1L, 9L, 1, "sam@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(lines, never()).saveAndFlush(any());
    }
    @Test
    void nonClientIsDeniedBeforeCartReadOrChanges() {
        when(shoppers.requireClient("sam@example.com"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Client only"));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.get(1L, "sam@example.com")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.setQuantity(1L, 9L, 1, "sam@example.com")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.remove(1L, 9L, "sam@example.com")).getStatusCode());
        verifyNoInteractions(carts, lines);
    }
}
