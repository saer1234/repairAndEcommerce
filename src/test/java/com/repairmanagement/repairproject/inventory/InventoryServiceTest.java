package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private ItemRepository items;
    private StockMovementRepository movements;
    private InventoryAccess access;
    private InventoryService service;
    private Item item;

    @BeforeEach
    void setup() {
        items = mock(ItemRepository.class);
        movements = mock(StockMovementRepository.class);
        access = mock(InventoryAccess.class);
        service = new InventoryService(items, movements, access);
        Company company = new Company("Shop", null, null, null, "EUR");
        item = new Item(company, "SCREEN-001", "Screen", null,
            new BigDecimal("50.00"), new BigDecimal("80.00"), 4);
        when(access.requireManager(1L, "owner@example.com")).thenReturn(mock(User.class));
    }

    @Test
    void restockAddsStockAndRecordsMovement() {
        when(items.lockForUpdate(1L, 7L)).thenReturn(Optional.of(item));
        StockItemView result = service.adjust(1L, 7L,
            new StockAdjustmentRequest(MovementType.RESTOCK, 3, "New delivery"), "owner@example.com");
        assertEquals(7, result.stockQuantity());
        verify(movements).save(argThat(m -> m.getMovementType() == MovementType.RESTOCK
            && m.getQuantityChange() == 3));
    }

    @Test
    void adjustmentCannotMakeStockNegative() {
        when(items.lockForUpdate(1L, 7L)).thenReturn(Optional.of(item));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.adjust(1L, 7L,
                new StockAdjustmentRequest(MovementType.ADJUSTMENT, -5, "Damaged items"),
                "owner@example.com"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(movements, never()).save(any());
    }

    @Test
    void manualSaleIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.adjust(1L, 7L,
                new StockAdjustmentRequest(MovementType.SALE, -1, "Try manual sale"),
                "owner@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(items, never()).lockForUpdate(any(), any());
    }

    @Test
    void employeeCannotManuallyChangeGlobalStock() {
        when(access.requireManager(1L, "employee@example.com"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Company manager permission required"));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () ->
            service.adjust(1L, 7L,
                new StockAdjustmentRequest(MovementType.RESTOCK, 2, "Unauthorized"), "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(items, never()).lockForUpdate(any(), any());
    }

    @Test
    void potentialProfitIsNotMisreportedAsRealizedProfit() {
        when(access.requireActiveCompany(1L)).thenReturn(item.getCompany());
        when(items.findByCompany_IdOrderByNameAsc(1L)).thenReturn(List.of(item));
        InventorySummaryView summary = service.summary(1L, "owner@example.com");
        assertEquals(new BigDecimal("200.00"), summary.inventoryCostValue());
        assertEquals(new BigDecimal("320.00"), summary.potentialSalesValue());
        assertEquals(new BigDecimal("120.00"), summary.potentialGrossProfit());
        assertEquals(4L, summary.unitsOnHand());
    }
}
