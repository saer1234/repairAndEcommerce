package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.UserService;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyAboutService {
    private final CompanyRepository companies;
    private final CompanyAboutRepository profiles;
    private final CompanyMemberRepository memberships;
    private final UserService users;

    public CompanyAboutService(CompanyRepository companies, CompanyAboutRepository profiles,
                               CompanyMemberRepository memberships, UserService users) {
        this.companies = companies; this.profiles = profiles; this.memberships = memberships; this.users = users;
    }
    @Transactional(readOnly = true)
    public CompanyAboutView publicAbout(Long companyId) {
        Company company = companies.findById(companyId)
            .filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        return CompanyAboutView.of(company, profiles.findById(companyId).orElse(null));
    }
    @Transactional
    public CompanyAboutView update(Long companyId, CompanyAboutRequest request, String email) {
        var user = users.currentUser(email);
        var membership = memberships.findByCompany_IdAndUser_IdAndActiveTrue(companyId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Company owner access required"));
        if (membership.getMemberRole() != MemberRole.OWNER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the company owner can edit About Us");
        Company company = companies.findById(companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        validateUrl(request.mapsUrl()); validateUrl(request.instagramUrl());
        validateUrl(request.facebookUrl()); validateUrl(request.tiktokUrl());
        validateUrl(request.whatsappUrl()); validateUrl(request.websiteUrl());
        CompanyAboutProfile profile = profiles.findById(companyId).orElseGet(() -> new CompanyAboutProfile(company));
        profile.apply(request);
        return CompanyAboutView.of(company, profiles.saveAndFlush(profile));
    }
    private void validateUrl(String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            URI url = new URI(raw.trim());
            String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || url.getHost() == null ||
                url.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Social and map URLs must be valid http(s) links");
        }
    }
}
