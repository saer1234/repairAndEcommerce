package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemCatalogVisibilityTest {
    @Test void clientCatalogUsesOnlyPublishedItems() {
        ItemRepository items = mock(ItemRepository.class);
        InventoryAccess access = mock(InventoryAccess.class);
        ItemService service = new ItemService(items, mock(StockMovementRepository.class), access);
        Company company = new Company("Shop", null, null, null, "EUR");
        ReflectionTestUtils.setField(company, "id", 2L);
        when(access.requireActiveCompany(2L)).thenReturn(company);
        when(items.findByCompany_IdAndActiveTrueAndVisibleToClientsTrueOrderByNameAsc(2L)).thenReturn(List.of());
        assertTrue(service.browse(2L).isEmpty());
        verify(items).findByCompany_IdAndActiveTrueAndVisibleToClientsTrueOrderByNameAsc(2L);
        verify(items, never()).findByCompany_IdOrderByNameAsc(anyLong());
    }

    @Test void hiddenItemCannotBeFetchedThroughClientDetail() {
        ItemRepository items = mock(ItemRepository.class);
        InventoryAccess access = mock(InventoryAccess.class);
        ItemService service = new ItemService(items, mock(StockMovementRepository.class), access);
        when(items.findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(3L, 2L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.detail(2L, 3L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
