package com.repairmanagement.repairproject.inventory;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/items")
public class ItemController {
    private final ItemService service;
    private final ItemImageService images;
    private final InventoryBulkImportService imports;
    public ItemController(ItemService service, ItemImageService images, InventoryBulkImportService imports) {
        this.service = service;
        this.images = images;
        this.imports = imports;
    }

    @GetMapping
    public List<PublicItemView> browse(@PathVariable Long companyId) {
        return service.browse(companyId);
    }

    @GetMapping("/{itemId}")
    public PublicItemView detail(@PathVariable Long companyId, @PathVariable Long itemId) {
        return service.detail(companyId, itemId);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InventoryBulkImportService.ImportPreview previewImport(@PathVariable Long companyId,
            @RequestPart("file") MultipartFile file, Principal principal) {
        return imports.preview(companyId, principal.getName(), file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryBulkImportService.ImportResult importItems(@PathVariable Long companyId,
            @RequestPart("file") MultipartFile file, Principal principal) {
        return imports.importFile(companyId, principal.getName(), file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagerItemView create(@PathVariable Long companyId,
                                   @Valid @RequestBody CreateItemRequest request, Principal principal) {
        return service.create(companyId, request, principal.getName());
    }

    @PutMapping("/{itemId}")
    public ManagerItemView update(@PathVariable Long companyId, @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateItemRequest request, Principal principal) {
        return service.update(companyId, itemId, request, principal.getName());
    }

    @PostMapping(value = "/{itemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ManagerItemView uploadImage(@PathVariable Long companyId, @PathVariable Long itemId,
            @RequestPart("file") MultipartFile file, Principal principal) {
        return images.upload(companyId, itemId, file, principal.getName());
    }

    @GetMapping("/{itemId}/image")
    public ResponseEntity<byte[]> downloadImage(@PathVariable Long companyId, @PathVariable Long itemId) {
        ItemImageService.ImageData result = images.get(companyId, itemId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.contentType()))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(result.data());
    }

    @DeleteMapping("/{itemId}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable Long companyId, @PathVariable Long itemId, Principal principal) {
        images.remove(companyId, itemId, principal.getName());
    }

    @PatchMapping("/{itemId}/visibility")
    public ManagerItemView setVisibility(@PathVariable Long companyId, @PathVariable Long itemId,
            @Valid @RequestBody UpdateItemVisibilityRequest request, Principal principal) {
        return service.setVisibility(companyId, itemId, request.visibleToClients(), principal.getName());
    }

    @PostMapping("/{itemId}/restore")
    public ManagerItemView restore(@PathVariable Long companyId, @PathVariable Long itemId, Principal principal) {
        return service.restore(companyId, itemId, principal.getName());
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long companyId, @PathVariable Long itemId, Principal principal) {
        service.deactivate(companyId, itemId, principal.getName());
    }
}
