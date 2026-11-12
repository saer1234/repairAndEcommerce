package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportServiceTest {
    SupportConversationRepository conversations = mock(SupportConversationRepository.class);
    SupportMessageRepository messages = mock(SupportMessageRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    CompanyMemberRepository memberships = mock(CompanyMemberRepository.class);
    UserService users = mock(UserService.class);
    SupportPresenceService presence = mock(SupportPresenceService.class);
    SupportClientConnections clients = mock(SupportClientConnections.class);
    SupportService service = new SupportService(conversations, messages, companies, memberships, users, presence, clients);
    Company shop;
    User client, otherClient, manager, employee;
    SupportConversation thread;

    @BeforeEach void setup() {
        shop = new Company("Shop", null, null, null, "USD");
        ReflectionTestUtils.setField(shop, "id", 1L);
        client = new User("Client One", "client@example.test", "hash", null, PlatformRole.CLIENT);
        otherClient = new User("Client Two", "other@example.test", "hash", null, PlatformRole.CLIENT);
        manager = new User("Shop Manager", "manager@example.test", "hash", null);
        employee = new User("Employee", "employee@example.test", "hash", null);
        ReflectionTestUtils.setField(client, "id", 10L);
        ReflectionTestUtils.setField(otherClient, "id", 11L);
        ReflectionTestUtils.setField(manager, "id", 12L);
        ReflectionTestUtils.setField(employee, "id", 13L);
        thread = new SupportConversation(shop, client);
        ReflectionTestUtils.setField(thread, "id", 200L);
        when(users.currentUser("client@example.test")).thenReturn(client);
        when(users.currentUser("other@example.test")).thenReturn(otherClient);
        when(users.currentUser("manager@example.test")).thenReturn(manager);
        when(users.currentUser("employee@example.test")).thenReturn(employee);
        when(conversations.findByIdAndCompany_Id(200L, 1L)).thenReturn(Optional.of(thread));
        when(presence.onlineCount(1L)).thenReturn(1L);
        when(clients.isOnline(1L, 10L)).thenReturn(true);
    }

    @Test void clientMayOpenOwnThread() {
        when(companies.findById(1L)).thenReturn(Optional.of(shop));
        when(conversations.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        SupportConversationView result = service.start(1L, "client@example.test");
        assertEquals(10L, result.customerId());
        verify(conversations).saveAndFlush(any(SupportConversation.class));
    }

    @Test void anotherClientCannotReadConversation() {
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.read(1L, 200L, "other@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(messages);
    }

    @Test void employeeCanReadCompanySupportInbox() {
        CompanyMember m = new CompanyMember(shop, employee, MemberRole.EMPLOYEE);
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(1L, 13L))
            .thenReturn(Optional.of(m));
        assertTrue(service.inbox(1L, "employee@example.test").isEmpty());
        verify(conversations).findByCompany_IdOrderByUpdatedAtDesc(1L);
    }

    @Test void managerCanReadMessagesAfterMembershipCheck() {
        CompanyMember m = new CompanyMember(shop, manager, MemberRole.MANAGER);
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(1L, 12L))
            .thenReturn(Optional.of(m));
        when(messages.findByConversation_IdOrderByIdDesc(eq(200L), any(Pageable.class)))
            .thenReturn(List.of(new SupportMessage(thread, client, "Hello")));
        var result = service.read(1L, 200L, "manager@example.test");
        assertEquals(1, result.size());
        assertEquals("CUSTOMER", result.getFirst().senderType());
    }

    @Test void clientCanReopenClosedChatWhenConnected() {
        thread.changeStatus(SupportStatus.CLOSED);
        when(messages.saveAndFlush(any(SupportMessage.class))).thenAnswer(i -> i.getArgument(0));
        var reply = service.send(1L, 200L, new SupportSendRequest("  Need help  "), "client@example.test");
        assertEquals("Need help", reply.body());
        assertEquals(SupportStatus.OPEN, thread.getStatus());
        assertEquals("CUSTOMER", reply.senderType());
    }

    @Test void staffCannotMessageDisconnectedClient() {
        CompanyMember m = new CompanyMember(shop, manager, MemberRole.MANAGER);
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(1L, 12L))
            .thenReturn(Optional.of(m));
        when(clients.isOnline(1L, 10L)).thenReturn(false);
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.send(1L, 200L, new SupportSendRequest("Hello"), "manager@example.test"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(messages);
    }

    @Test void closingOfflineClientKeepsHistory() {
        when(conversations.findByCompany_IdAndCustomer_Id(1L, 10L)).thenReturn(Optional.of(thread));
        assertFalse(service.closeWhenClientOffline(1L, 10L)); // online
        when(clients.isOnline(1L, 10L)).thenReturn(false);
        assertTrue(service.closeWhenClientOffline(1L, 10L));
        assertEquals(SupportStatus.CLOSED, thread.getStatus());
        assertFalse(service.closeWhenClientOffline(1L, 10L));
        verifyNoInteractions(messages);
    }

    @Test void reconnectAutomaticallyReopensExistingConversationWithoutNewMessages() {
        thread.changeStatus(SupportStatus.CLOSED);
        when(conversations.findByCompany_IdAndCustomer_Id(1L, 10L)).thenReturn(Optional.of(thread));
        assertTrue(service.reopenWhenClientOnline(1L, 10L));
        assertEquals(SupportStatus.OPEN, thread.getStatus());
        assertFalse(service.reopenWhenClientOnline(1L, 10L));
        verify(conversations, never()).saveAndFlush(any());
        verifyNoInteractions(messages);
    }

    @Test void reconnectDoesNotReopenWhenCustomerIsOffline() {
        thread.changeStatus(SupportStatus.CLOSED);
        when(clients.isOnline(1L, 10L)).thenReturn(false);
        assertFalse(service.reopenWhenClientOnline(1L, 10L));
        assertEquals(SupportStatus.CLOSED, thread.getStatus());
        verifyNoInteractions(conversations);
    }

    @Test void startingExistingConversationReopensWithoutDuplicatingIt() {
        thread.changeStatus(SupportStatus.CLOSED);
        when(conversations.findByCompany_IdAndCustomer_Id(1L, 10L)).thenReturn(Optional.of(thread));
        var result = service.start(1L, "client@example.test");
        assertEquals(SupportStatus.OPEN, result.status());
        verify(conversations, never()).saveAndFlush(any());
    }

    @Test void existingLiveThreadCannotBeOpenedWhileShopOffline() {
        when(presence.onlineCount(1L)).thenReturn(0L);
        when(conversations.findByCompany_IdAndCustomer_Id(1L, 10L)).thenReturn(Optional.of(thread));
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.start(1L, "client@example.test"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test void customerCannotSendLiveMessageWhileShopOffline() {
        when(presence.onlineCount(1L)).thenReturn(0L);
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.send(1L, 200L, new SupportSendRequest("hello"), "client@example.test"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(messages);
    }

    @Test void employeeCannotCloseConversation() {
        CompanyMember m = new CompanyMember(shop, employee, MemberRole.EMPLOYEE);
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(1L, 13L))
            .thenReturn(Optional.of(m));
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.setStatus(1L, 200L, new SupportStatusRequest(SupportStatus.CLOSED), "employee@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test void onlyOwnerOrManagerMayCloseConversation() {
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.setStatus(1L, 200L, new SupportStatusRequest(SupportStatus.CLOSED), "client@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
