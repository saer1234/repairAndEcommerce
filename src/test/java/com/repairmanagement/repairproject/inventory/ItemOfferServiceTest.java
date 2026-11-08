package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemOfferServiceTest {
    @Test void managerCanConfigureAndRemoveDiscount() {
        ItemRepository repo = mock(ItemRepository.class);
        InventoryAccess access = mock(InventoryAccess.class);
        Item item = new Item(new Company("Shop", null, null, null, "USD"), "P1", "Part", "",
            new BigDecimal("20.00"), new BigDecimal("100.00"), 4);
        when(repo.lockForUpdate(7L, 5L)).thenReturn(Optional.of(item));
        ItemOfferService service = new ItemOfferService(repo, access);
        assertEquals(new BigDecimal("85.00"), service.set(7L, 5L, new SetItemOfferRequest(15), "boss@shop.test").effectivePrice());
        assertEquals(15, item.getDiscountPercent());
        assertEquals(new BigDecimal("100.00"), service.remove(7L, 5L, "boss@shop.test").effectivePrice());
        verify(access, times(2)).requireManager(7L, "boss@shop.test");
    }

    @Test void employeeCannotChangeOffer() {
        ItemRepository repo = mock(ItemRepository.class);
        InventoryAccess access = mock(InventoryAccess.class);
        when(access.requireManager(7L, "employee@shop.test"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        ItemOfferService service = new ItemOfferService(repo, access);
        assertThrows(ResponseStatusException.class,
            () -> service.set(7L, 5L, new SetItemOfferRequest(20), "employee@shop.test"));
        verifyNoInteractions(repo);
    }
}
