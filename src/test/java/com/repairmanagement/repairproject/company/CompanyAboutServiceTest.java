package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyAboutServiceTest {
    CompanyRepository companies = mock(CompanyRepository.class);
    CompanyAboutRepository profiles = mock(CompanyAboutRepository.class);
    CompanyMemberRepository memberships = mock(CompanyMemberRepository.class);
    UserService users = mock(UserService.class);
    CompanyAboutService service = new CompanyAboutService(companies, profiles, memberships, users);
    Company company = new Company("Acme Repairs", "acme@example.test", "+96170000000", "Main St", "USD");
    User owner = new User("Owner", "owner@example.test", "hash", null);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(company, "id", 15L);
        ReflectionTestUtils.setField(owner, "id", 4L);
        when(users.currentUser("owner@example.test")).thenReturn(owner);
        when(companies.findById(15L)).thenReturn(Optional.of(company));
    }
    private CompanyAboutRequest request(String instagram) {
        return new CompanyAboutRequest("Our shop", "We repair phones", "Beirut", "+96171123456",
            "hello@example.test", "https://maps.google.com/", instagram,
            null, null, null, "https://example.com", "9-5");
    }
    @Test void publicPageUsesExistingCompanyContactUntilOwnerPublishesProfile() {
        var view = service.publicAbout(15L);
        assertEquals("About Acme Repairs", view.headline());
        assertEquals("Main St", view.storeAddress());
        assertEquals("+96170000000", view.contactPhone());
    }
    @Test void ownerMayPublishContactAndSocialInformation() {
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(15L, 4L))
            .thenReturn(Optional.of(new CompanyMember(company, owner, MemberRole.OWNER)));
        when(profiles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var view = service.update(15L, request("https://instagram.com/acme"), "owner@example.test");
        assertEquals("Beirut", view.storeAddress());
        assertEquals("https://instagram.com/acme", view.instagramUrl());
        verify(profiles).saveAndFlush(any(CompanyAboutProfile.class));
    }
    @Test void managerCannotEditCompanyAboutPage() {
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(15L, 4L))
            .thenReturn(Optional.of(new CompanyMember(company, owner, MemberRole.MANAGER)));
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.update(15L, request(null), "owner@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(profiles);
    }
    @Test void javascriptUrlIsRejected() {
        when(memberships.findByCompany_IdAndUser_IdAndActiveTrue(15L, 4L))
            .thenReturn(Optional.of(new CompanyMember(company, owner, MemberRole.OWNER)));
        var ex = assertThrows(ResponseStatusException.class, () ->
            service.update(15L, request("javascript:alert(1)"), "owner@example.test"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(profiles, never()).saveAndFlush(any());
    }
}
