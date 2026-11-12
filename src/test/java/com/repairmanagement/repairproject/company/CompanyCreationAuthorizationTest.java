package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyCreationAuthorizationTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyMemberRepository members = mock(CompanyMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final CompanyService service = new CompanyService(companies, members, users, userService, mock(org.springframework.context.ApplicationEventPublisher.class));
    private final CompanyRequest body = new CompanyRequest("Demo", null, null, null, "EUR");

    @Test void clientCannotCreateCompany() {
        User client = new User("Client", "client@example.com", "hash", null, PlatformRole.CLIENT);
        when(userService.currentUser("client@example.com")).thenReturn(client);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(body, "client@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(companies, members);
    }

    @Test void ownerCanCreateCompany() {
        User owner = new User("Owner", "owner@example.com", "hash", null, PlatformRole.OWNER);
        when(userService.currentUser("owner@example.com")).thenReturn(owner);
        when(companies.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        CompanyView result = service.create(body, "owner@example.com");
        assertEquals(MemberRole.OWNER, result.yourRole());
        verify(members).save(any(CompanyMember.class));
    }
}
