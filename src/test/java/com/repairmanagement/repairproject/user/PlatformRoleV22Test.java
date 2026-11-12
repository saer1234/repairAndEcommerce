package com.repairmanagement.repairproject.user;

import com.repairmanagement.repairproject.company.CompanyMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformRoleV22Test {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService auth = mock(UserService.class);
    private final CompanyMemberRepository members = mock(CompanyMemberRepository.class);
    private final PlatformUsersService service = new PlatformUsersService(users, auth, members);

    @Test void adminMayEditOwnerRoleButNotGrantSuperAdmin() {
        User admin = mock(User.class);
        when(admin.getPlatformRole()).thenReturn(PlatformRole.SUPER_ADMIN);
        when(admin.getId()).thenReturn(1L);
        when(auth.currentUser("admin@test.local")).thenReturn(admin);
        User target = new User("Member", "member@test.local", "hash", null, PlatformRole.USER);
        ReflectionTestUtils.setField(target, "id", 2L);
        when(users.findById(2L)).thenReturn(Optional.of(target));
        assertEquals(PlatformRole.OWNER,
            service.changeRole("admin@test.local", 2L, new ChangePlatformRoleRequest(PlatformRole.OWNER)).platformRole());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.changeRole("admin@test.local", 2L,
                new ChangePlatformRoleRequest(PlatformRole.SUPER_ADMIN))).getStatusCode());
    }

    @Test void staffCannotBeConvertedToClientWithoutMembershipRemoval() {
        User admin = mock(User.class);
        when(admin.getPlatformRole()).thenReturn(PlatformRole.SUPER_ADMIN);
        when(admin.getId()).thenReturn(1L);
        when(auth.currentUser("admin@test.local")).thenReturn(admin);
        User target = new User("Member", "member@test.local", "hash", null, PlatformRole.OWNER);
        ReflectionTestUtils.setField(target, "id", 2L);
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(members.existsByUser_IdAndActiveTrue(2L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.changeRole("admin@test.local", 2L,
                new ChangePlatformRoleRequest(PlatformRole.CLIENT))).getStatusCode());
    }
}
