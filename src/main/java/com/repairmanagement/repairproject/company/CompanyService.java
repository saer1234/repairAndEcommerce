package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.PlatformRole;
import com.repairmanagement.repairproject.user.UserRepository;
import com.repairmanagement.repairproject.user.UserService;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final UserRepository users;
    private final UserService userService;

    public CompanyService(CompanyRepository companies, CompanyMemberRepository members,
                          UserRepository users, UserService userService) {
        this.companies = companies;
        this.members = members;
        this.users = users;
        this.userService = userService;
    }

    @Transactional
    public CompanyView create(CompanyRequest request, String authenticatedEmail) {
        User owner = userService.currentUser(authenticatedEmail);
        // Do not rely solely on a hidden Angular button: enforce this rule server-side.
        if (owner.getPlatformRole() != PlatformRole.OWNER &&
            owner.getPlatformRole() != PlatformRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only owner accounts may create a company");
        }
        String currency = request.currencyCode().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO currency code");
        }
        Company company = companies.save(new Company(
            request.name().trim(), request.email(), request.phone(), request.address(), currency));
        members.save(new CompanyMember(company, owner, MemberRole.OWNER));
        return CompanyView.of(company, MemberRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<CompanyView> mine(String authenticatedEmail) {
        User user = userService.currentUser(authenticatedEmail);
        return members.findByUser_IdAndActiveTrue(user.getId()).stream()
            .map(m -> CompanyView.of(m.getCompany(), m.getMemberRole()))
            .toList();
    }

    @Transactional(readOnly = true)
    public CompanyView get(Long companyId, String authenticatedEmail) {
        User user = userService.currentUser(authenticatedEmail);
        CompanyMember membership = requireActiveMember(companyId, user.getId());
        Company company = companies.findById(companyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        return CompanyView.of(company, membership.getMemberRole());
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(Long companyId, String authenticatedEmail) {
        User actor = userService.currentUser(authenticatedEmail);
        MemberRole role = requireActiveMember(companyId, actor.getId()).getMemberRole();
        requireManager(role);
        return members.findByCompany_IdAndActiveTrue(companyId).stream().map(MemberView::of).toList();
    }

    @Transactional
    public MemberView addMember(Long companyId, AddMemberRequest request, String authenticatedEmail) {
        User actor = userService.currentUser(authenticatedEmail);
        MemberRole actorRole = requireActiveMember(companyId, actor.getId()).getMemberRole();
        requireManager(actorRole);

        if (request.role() == MemberRole.OWNER ||
            (request.role() == MemberRole.MANAGER && actorRole != MemberRole.OWNER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted to assign that role");
        }
        Company company = companies.findById(companyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company is not active");
        }
        User target = users.findByEmailIgnoreCase(request.email().trim()).filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "User not found; ask them to register first"));
        if (members.existsByCompany_IdAndUser_Id(companyId, target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a membership record");
        }
        CompanyMember newMember = members.saveAndFlush(new CompanyMember(company, target, request.role()));
        return MemberView.of(newMember);
    }

    private CompanyMember requireActiveMember(Long companyId, Long userId) {
        // Responding 404 also avoids revealing whether another tenant exists.
        return members.findByCompany_IdAndUser_IdAndActiveTrue(companyId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private void requireManager(MemberRole role) {
        if (role != MemberRole.OWNER && role != MemberRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager permission required");
        }
    }
}
