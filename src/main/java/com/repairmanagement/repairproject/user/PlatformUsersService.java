package com.repairmanagement.repairproject.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import com.repairmanagement.repairproject.company.CompanyMemberRepository;

@Service
public class PlatformUsersService {
    private final UserRepository users;
    private final UserService userService;
    private final CompanyMemberRepository memberships;

    public PlatformUsersService(UserRepository users, UserService userService, CompanyMemberRepository memberships) {
        this.users = users;
        this.userService = userService;
        this.memberships = memberships;
    }

    @Transactional
    public PlatformUserView changeRole(String authenticatedEmail, Long targetId, ChangePlatformRoleRequest request) {
        User caller = userService.currentUser(authenticatedEmail);
        if (caller.getPlatformRole() != PlatformRole.SUPER_ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator required");
        User target = users.findById(targetId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (caller.getId().equals(targetId) || target.getPlatformRole() == PlatformRole.SUPER_ADMIN ||
            request.role() == null || request.role() == PlatformRole.SUPER_ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change super-admin privileges here");
        if (request.role() == PlatformRole.CLIENT && memberships.existsByUser_IdAndActiveTrue(targetId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove company memberships before making a client account");
        target.changePlatformRole(request.role());
        return PlatformUserView.of(target);
    }

    @Transactional(readOnly = true)
    public PlatformUsersPage list(String authenticatedEmail, String search, int page, int size) {
        User caller = userService.currentUser(authenticatedEmail);
        // Every shop owner may be publicly registered: only the separately provisioned
        // platform administrator may see the website-wide customer directory.
        if (caller.getPlatformRole() != PlatformRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform owner access required");
        }
        if (page < 0 || page > 100_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or page size");
        }
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is too long");
        }
        Page<User> result = users.searchPlatformUsers(term,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PlatformUsersPage(result.getContent().stream().map(PlatformUserView::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
