package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemImageServiceTest {
    @TempDir Path dir;
    private ItemRepository repository;
    private InventoryAccess access;
    private ItemImageService service;
    private Item item;

    @BeforeEach
    void setup() {
        repository = mock(ItemRepository.class);
        access = mock(InventoryAccess.class);
        service = new ItemImageService(dir.toString(), repository, access);
        item = new Item(new Company("Shop", null, null, null, "EUR"),
            "SCREEN-001", "Screen", null, new BigDecimal("50.00"),
            new BigDecimal("80.00"), 2);
        when(repository.lockForUpdate(1L, 7L)).thenReturn(Optional.of(item));
        when(repository.findByIdAndCompany_IdAndActiveTrue(7L, 1L)).thenReturn(Optional.of(item));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsNonImageDespiteFakeFilenameAndContentType() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.upload(1L, 7L, fake, "owner@example.com"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
        assertNull(item.getImageFilename());
        verify(repository, never()).flush();
    }

    @Test
    void preventsUsersFromOtherCompaniesChangingImages() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access)
            .requireManager(1L, "stranger@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", new byte[]{1,2,3});
        assertThrows(ResponseStatusException.class,
            () -> service.upload(1L, 7L, file, "stranger@example.com"));
        verify(repository, never()).lockForUpdate(any(), any());
    }

    @Test
    void storesImageAndDeletesItOnRemoveAfterCommit() throws Exception {
        // A valid 1 x 1 transparent PNG.
        byte[] bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ1sAAAAASUVORK5CYII=");
        MockMultipartFile image = new MockMultipartFile("file", "../../../evil.svg", "image/svg+xml", bytes);
        service.upload(1L, 7L, image, "owner@example.com");
        assertEquals("image/png", item.getImageContentType()); // sniffed, not client supplied
        String filename = item.getImageFilename();
        assertTrue(filename.matches("[0-9a-f-]{36}\\.png"));
        assertArrayEquals(bytes, service.get(1L, 7L).data());
        try (var files = Files.list(dir)) { assertEquals(1L, files.count()); }
        // In a real runtime Spring invokes these methods when the DB transaction completes.
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        service.remove(1L, 7L, "owner@example.com");
        assertNull(item.getImageFilename());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertFalse(Files.exists(dir.resolve(filename)));
    }

    @Test
    void rollsBackImageFileIfDatabaseTransactionFails() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ1sAAAAASUVORK5CYII=");
        service.upload(1L, 7L, new MockMultipartFile("file", "a.png", "image/png", bytes), "owner@example.com");
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        try (var files = Files.list(dir)) { assertEquals(0L, files.count()); }
    }
}
