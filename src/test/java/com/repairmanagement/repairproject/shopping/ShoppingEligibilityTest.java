package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.CompanyMemberRepository;
import com.repairmanagement.repairproject.user.PlatformRole;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShoppingEligibilityTest {
    private UserService users;
    private CompanyMemberRepository memberships;
    private ShoppingEligibility eligibility;

    @BeforeEach
    void setup() {
        users = mock(UserService.class);
        memberships = mock(CompanyMemberRepository.class);
        eligibility = new ShoppingEligibility(users, memberships);
    }

    private User account(PlatformRole role, long id) {
        User account = role == PlatformRole.USER
            ? new User("Customer", "customer@example.com", "hash", null)
            : new User("Customer", "customer@example.com", "hash", null, role);
        ReflectionTestUtils.setField(account, "id", id);
        when(users.currentUser("customer@example.com")).thenReturn(account);
        return account;
    }

    @Test
    void clientWithNoActiveMembershipCanShop() {
        User client = account(PlatformRole.CLIENT, 15L);
        assertSame(client, eligibility.requireClient("customer@example.com"));
        verify(memberships).existsByUser_IdAndActiveTrue(15L);
    }

    @Test
    void ownerCannotShop() {
        account(PlatformRole.OWNER, 15L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> eligibility.requireClient("customer@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(memberships);
    }

    @Test
    void legacyUserCannotShopWithoutClientAccountType() {
        account(PlatformRole.USER, 15L);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> eligibility.requireClient("customer@example.com")).getStatusCode());
    }

    @Test
    void employeeOrManagerMembershipPreventsShoppingEvenWhenAccountIsClient() {
        account(PlatformRole.CLIENT, 15L);
        when(memberships.existsByUser_IdAndActiveTrue(15L)).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> eligibility.requireClient("customer@example.com")).getStatusCode());
    }
}
