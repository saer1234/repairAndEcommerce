package com.repairmanagement.repairproject.company;

import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.PlatformRole;
import com.repairmanagement.repairproject.user.UserRepository;
import com.repairmanagement.repairproject.user.UserService;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public CompanyService(CompanyRepository companies, CompanyMemberRepository members,
                          UserRepository users, UserService userService, ApplicationEventPublisher events) {
        this.companies = companies;
        this.members = members;
        this.users = users;
        this.userService = userService;
        this.events = events;
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

    /** Only the current company owner can edit roles; the last owner is protected. */
    @Transactional
    public MemberView changeMemberRole(Long companyId, Long userId,
                                       ChangeMemberRoleRequest request, String email) {
        User actor = userService.currentUser(email);
        companies.lockForMembershipChange(companyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        requireOwner(companyId, actor.getId());
        if (request.role() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role required");
        CompanyMember target = members.findByCompany_IdAndUser_IdAndActiveTrue(companyId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active member not found"));
        if (actor.getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot edit your own company role");
        if (target.getMemberRole() == MemberRole.OWNER && request.role() != MemberRole.OWNER)
            requireOtherOwner(companyId);
        if (request.role() == MemberRole.OWNER && !target.getUser().isActive())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target account inactive");
        if (target.getMemberRole() != request.role()) {
            target.changeRole(request.role());
            events.publishEvent(new CompanyMembershipChanged(companyId, userId));
        }
        return MemberView.of(target);
    }

    /** Removes membership, presence and support access; no conversation history is deleted. */
    @Transactional
    public void removeMember(Long companyId, Long userId, String email) {
        User actor = userService.currentUser(email);
        companies.lockForMembershipChange(companyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        requireOwner(companyId, actor.getId());
        if (actor.getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove yourself");
        CompanyMember target = members.findByCompany_IdAndUser_IdAndActiveTrue(companyId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active member not found"));
        if (target.getMemberRole() == MemberRole.OWNER) requireOtherOwner(companyId);
        target.deactivate();
        events.publishEvent(new CompanyMembershipChanged(companyId, userId));
    }

    /** Re-add a previously removed user without violating the unique company/user constraint. */
    @Transactional
    public MemberView restoreMember(Long companyId, Long userId, ChangeMemberRoleRequest request, String email) {
        User actor = userService.currentUser(email);
        companies.lockForMembershipChange(companyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        requireOwner(companyId, actor.getId());
        if (request.role() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role required");
        CompanyMember target = members.findByCompany_IdAndUser_Id(companyId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Former member not found"));
        if (target.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Member already active");
        if (!target.getUser().isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "User inactive");
        target.reactivate(request.role());
        events.publishEvent(new CompanyMembershipChanged(companyId, userId));
        return MemberView.of(target);
    }

    private void requireOwner(Long companyId, Long userId) {
        if (requireActiveMember(companyId, userId).getMemberRole() != MemberRole.OWNER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the company owner can change roles");
    }

    private void requireOtherOwner(Long companyId) {
        if (members.countByCompany_IdAndActiveTrueAndMemberRole(companyId, MemberRole.OWNER) <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last company owner");
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
