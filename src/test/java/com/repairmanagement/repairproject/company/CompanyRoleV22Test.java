package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyRoleV22Test {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyMemberRepository members = mock(CompanyMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserService auth = mock(UserService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CompanyService service = new CompanyService(companies, members, users, auth, events);

    private User account(Long id, String email) {
        User user = new User("Test", email, "hash", null, PlatformRole.OWNER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test void managerCannotEditRoles() {
        User manager = account(1L, "manager@test.local");
        when(auth.currentUser(manager.getEmail())).thenReturn(manager);
        when(companies.lockForMembershipChange(10L)).thenReturn(Optional.of(mock(Company.class)));
        CompanyMember membership = new CompanyMember(mock(Company.class), manager, MemberRole.MANAGER);
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(membership));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changeMemberRole(10L, 2L, new ChangeMemberRoleRequest(MemberRole.OWNER), manager.getEmail()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(events);
    }

    @Test void ownerRemovalRevokesMembershipAndPublishesEvent() {
        User owner = account(1L, "owner@test.local");
        User employee = account(2L, "staff@test.local");
        Company shop = mock(Company.class);
        CompanyMember ownerMembership = new CompanyMember(shop, owner, MemberRole.OWNER);
        CompanyMember target = new CompanyMember(shop, employee, MemberRole.EMPLOYEE);
        when(auth.currentUser(owner.getEmail())).thenReturn(owner);
        when(companies.lockForMembershipChange(10L)).thenReturn(Optional.of(shop));
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(ownerMembership));
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(10L, 2L)).thenReturn(Optional.of(target));
        service.removeMember(10L, 2L, owner.getEmail());
        assertFalse(target.isActive());
        verify(events).publishEvent(new CompanyMembershipChanged(10L, 2L));
    }

    @Test void lastOwnerCannotBeRemoved() {
        User owner = account(1L, "owner@test.local");
        User otherOwner = account(2L, "other@test.local");
        Company shop = mock(Company.class);
        when(auth.currentUser(owner.getEmail())).thenReturn(owner);
        when(companies.lockForMembershipChange(10L)).thenReturn(Optional.of(shop));
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(10L, 1L))
            .thenReturn(Optional.of(new CompanyMember(shop, owner, MemberRole.OWNER)));
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(10L, 2L))
            .thenReturn(Optional.of(new CompanyMember(shop, otherOwner, MemberRole.OWNER)));
        when(members.countByCompany_IdAndActiveTrueAndMemberRole(10L, MemberRole.OWNER)).thenReturn(1L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.removeMember(10L, 2L, owner.getEmail())).getStatusCode());
        verifyNoInteractions(events);
    }
}
