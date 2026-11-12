package com.repairmanagement.repairproject.user;

import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/users")
public class PlatformUsersController {
    private final PlatformUsersService service;
    public PlatformUsersController(PlatformUsersService service) {
        this.service = service;
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<PlatformUserView> changeRole(Principal principal, @PathVariable Long userId,
            @Valid @RequestBody ChangePlatformRoleRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.changeRole(principal.getName(), userId, body));
    }

    @GetMapping
    public ResponseEntity<PlatformUsersPage> list(Principal principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(principal.getName(), search, page, size));
    }
}
