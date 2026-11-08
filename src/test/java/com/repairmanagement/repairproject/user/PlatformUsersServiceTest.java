package com.repairmanagement.repairproject.user;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformUsersServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PlatformUsersService service = new PlatformUsersService(users, userService);

    @Test void companyOwnerCannotListEveryone() {
        when(userService.currentUser("owner@example.com")).thenReturn(
                new User("Shop Owner", "owner@example.com", "hash", null, PlatformRole.OWNER));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list("owner@example.com", "", 0, 20));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(users);
    }

    @Test void clientCannotListEveryone() {
        when(userService.currentUser("client@example.com")).thenReturn(
                new User("Customer", "client@example.com", "hash", null, PlatformRole.CLIENT));
        assertThrows(ResponseStatusException.class,
                () -> service.list("client@example.com", "", 0, 20));
        verifyNoInteractions(users);
    }

    @Test void platformAdministratorCanListUsersWithoutPasswordHashes() {
        User admin = mock(User.class);
        when(admin.getPlatformRole()).thenReturn(PlatformRole.SUPER_ADMIN);
        when(userService.currentUser("admin@example.com")).thenReturn(admin);
        User customer = new User("Customer", "customer@example.com", "secret-hash", "123", PlatformRole.CLIENT);
        when(users.searchPlatformUsers(eq("customer"), any())).thenReturn(
                new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1));
        PlatformUsersPage result = service.list("admin@example.com", "  CUSTOMER ", 0, 20);
        assertEquals(1, result.totalUsers());
        assertEquals("customer@example.com", result.users().getFirst().email());
        assertEquals("123", result.users().getFirst().phone());
        assertFalse(java.util.Arrays.stream(PlatformUserView.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
    }

    @Test void rejectsHugePageSize() {
        User admin = mock(User.class);
        when(admin.getPlatformRole()).thenReturn(PlatformRole.SUPER_ADMIN);
        when(userService.currentUser("admin@example.com")).thenReturn(admin);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list("admin@example.com", "", 0, 1000));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(users);
    }
}
