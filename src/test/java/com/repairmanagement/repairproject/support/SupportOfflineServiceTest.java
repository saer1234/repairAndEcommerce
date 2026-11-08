package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportOfflineServiceTest {
    SupportOfflineMessageRepository repository = mock(SupportOfflineMessageRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    CompanyMemberRepository memberships = mock(CompanyMemberRepository.class);
    UserService users = mock(UserService.class);
    SupportPresenceService presence = mock(SupportPresenceService.class);
    SupportOfflineService service = new SupportOfflineService(repository, companies, memberships, users, presence);
    Company shop = new Company("Shop", null, null, null, "USD");
    User customer = new User("Client", "client@example.test", "hash", null, PlatformRole.CLIENT);
    User employee = new User("Employee", "staff@example.test", "hash", null);

    SupportOfflineServiceTest() {
        ReflectionTestUtils.setField(shop, "id", 10L);
        ReflectionTestUtils.setField(customer, "id", 20L);
        ReflectionTestUtils.setField(employee, "id", 21L);
        when(users.currentUser("client@example.test")).thenReturn(customer);
        when(users.currentUser("staff@example.test")).thenReturn(employee);
    }

    @Test void clientCanLeaveMessageWhileOffline() {
        when(companies.findById(10L)).thenReturn(Optional.of(shop));
        when(repository.saveAndFlush(any(SupportOfflineMessage.class))).thenAnswer(i -> i.getArgument(0));
        var created = service.submit(10L, new SupportSendRequest("  Help me  "), "client@example.test");
        assertEquals("Help me", created.body());
        assertEquals(20L, created.customerId());
    }

    @Test void staffCannotSubmitClientOfflineMessage() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () ->
            service.submit(10L, new SupportSendRequest("message"), "staff@example.test")).getStatusCode());
    }

    @Test void staffCanSeeOwnCompanyOfflineInbox() {
        when(repository.findTop100ByCompany_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        assertTrue(service.inbox(10L, "staff@example.test").isEmpty());
        verify(presence).requireStaff(10L, "staff@example.test");
    }

    @Test void staffCannotReplyToDifferentCompanyMessage() {
        when(repository.findByIdAndCompany_Id(33L, 10L)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.reply(10L, 33L, new SupportOfflineReplyRequest("Hello"), "staff@example.test"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test void staffCanReplyOnceAndCustomerCanReadLater() {
        SupportOfflineMessage inquiry = new SupportOfflineMessage(shop, customer, "Need help");
        ReflectionTestUtils.setField(inquiry, "id", 55L);
        when(repository.findByIdAndCompany_Id(55L, 10L)).thenReturn(Optional.of(inquiry));
        when(repository.saveAndFlush(any(SupportOfflineMessage.class))).thenAnswer(i -> i.getArgument(0));
        var reply = service.reply(10L, 55L, new SupportOfflineReplyRequest("  We can help  "), "staff@example.test");
        assertEquals("We can help", reply.reply());
        assertEquals("Employee", reply.repliedByName());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () ->
            service.reply(10L, 55L, new SupportOfflineReplyRequest("Second"), "staff@example.test")).getStatusCode());
    }
}
