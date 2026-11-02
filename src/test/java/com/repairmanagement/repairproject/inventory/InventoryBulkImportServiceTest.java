package com.repairmanagement.repairproject.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.repairmanagement.repairproject.user.User;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class InventoryBulkImportServiceTest {
    private final InventoryAccess access = mock(InventoryAccess.class);
    private final ItemRepository repository = mock(ItemRepository.class);
    private final ItemService itemService = mock(ItemService.class);
    private InventoryBulkImportService service;

    @BeforeEach
    void setUp() {
        service = new InventoryBulkImportService(access, repository, itemService,
            Validation.buildDefaultValidatorFactory().getValidator());
        when(access.requireManager(eq(1L), eq("owner@shop.test"))).thenReturn(mock(User.class));
        when(repository.findByCompany_IdOrderByNameAsc(1L)).thenReturn(List.of());
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "items.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsValidProductsAndSnapshotsOpeningStock() {
        MockMultipartFile file = csv("sku,name,deviceType,deviceBrand,costPrice,sellingPrice,openingStock,category,compatibleDevices\n" +
            "APP-IP13-SCR,iPhone 13 screen,Phone,Apple,39.00,89.00,7,Screen,Phone|Apple|iPhone 13\n");
        var preview = service.preview(1L, "owner@shop.test", file);
        assertEquals(1, preview.validRows());
        assertEquals(0, preview.skippedExisting());
        assertTrue(preview.errors().isEmpty());
        var result = service.importFile(1L, "owner@shop.test", file);
        assertEquals(1, result.created());
        verify(itemService).create(eq(1L), argThat(r ->
            r.sku().equals("APP-IP13-SCR") && r.openingStock() == 7 && r.compatibleDevices().size() == 1),
            eq("owner@shop.test"));
    }

    @Test
    void skipsExistingSkuWithoutUpdatingItsStock() {
        Item existing = mock(Item.class);
        when(existing.getSku()).thenReturn("APP-IP13-SCR");
        when(repository.findByCompany_IdOrderByNameAsc(1L)).thenReturn(List.of(existing));
        MockMultipartFile file = csv("sku,name,deviceType,deviceBrand,costPrice,sellingPrice,openingStock\n" +
            "APP-IP13-SCR,iPhone 13 screen,Phone,Apple,39.00,89.00,7\n");
        var report = service.preview(1L, "owner@shop.test", file);
        assertEquals(1, report.skippedExisting());
        assertEquals(0, report.validRows());
        assertEquals(0, service.importFile(1L, "owner@shop.test", file).created());
        verifyNoInteractions(itemService);
    }

    @Test
    void invalidRowAbortsWholeImport() {
        MockMultipartFile file = csv("sku,name,deviceType,deviceBrand,costPrice,sellingPrice,openingStock\n" +
            "OK-001,Good,Phone,Apple,10.00,20.00,3\n" +
            "FAIL-002,Bad,Phone,Apple,abc,20.00,3\n");
        var report = service.preview(1L, "owner@shop.test", file);
        assertEquals(2, report.totalRows());
        assertEquals(1, report.errors().size());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
            () -> service.importFile(1L, "owner@shop.test", file));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verifyNoInteractions(itemService);
    }

    @Test
    void nonManagerCannotPreviewOrImport() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access)
            .requireManager(1L, "employee@shop.test");
        MockMultipartFile file = csv("sku,name,deviceType,deviceBrand,costPrice,sellingPrice,openingStock\n" +
            "OK-001,Good,Phone,Apple,10.00,20.00,3\n");
        assertThrows(ResponseStatusException.class,
            () -> service.preview(1L, "employee@shop.test", file));
        assertThrows(ResponseStatusException.class,
            () -> service.importFile(1L, "employee@shop.test", file));
    }
}
