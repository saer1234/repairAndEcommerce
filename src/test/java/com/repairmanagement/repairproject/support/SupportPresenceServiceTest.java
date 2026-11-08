package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportPresenceServiceTest {
    SupportAgentPresenceRepository repository = mock(SupportAgentPresenceRepository.class);
    CompanyMemberRepository membership = mock(CompanyMemberRepository.class);
    CompanyRepository companyRepository = mock(CompanyRepository.class);
    UserService users = mock(UserService.class);
    SupportPresenceService service = new SupportPresenceService(repository, membership, companyRepository, users);
    Company shop = new Company("Shop", null, null, null, "USD");
    User employee = new User("Employee", "staff@example.test", "hash", null);

    SupportPresenceServiceTest() {
        ReflectionTestUtils.setField(shop, "id", 10L);
        ReflectionTestUtils.setField(employee, "id", 20L);
        when(users.currentUser("staff@example.test")).thenReturn(employee);
    }

    @Test void employeeCanActivatePresenceInOwnCompany() {
        when(membership.findByCompany_IdAndUser_IdAndActiveTrue(10L, 20L))
            .thenReturn(Optional.of(new CompanyMember(shop, employee, MemberRole.EMPLOYEE)));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(repository.saveAndFlush(any(SupportAgentPresence.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.countAvailableAgents(eq(10L), any(), any())).thenReturn(1L);
        var result = service.heartbeat(10L,
            new SupportHeartbeatRequest("123e4567-e89b-12d3-a456-426614174000"), "staff@example.test");
        assertTrue(result.online());
        assertEquals(1L, result.availableAgents());
        verify(repository).saveAndFlush(any(SupportAgentPresence.class));
    }

    @Test void employeeCannotActivatePresenceForOtherCompany() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.heartbeat(11L,
            new SupportHeartbeatRequest("123e4567-e89b-12d3-a456-426614174000"), "staff@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test void onlyActiveHeartbeatCountsAsOnline() {
        when(repository.countAvailableAgents(eq(10L), any(), any())).thenReturn(0L);
        assertEquals(0L, service.onlineCount(10L));
    }
}
