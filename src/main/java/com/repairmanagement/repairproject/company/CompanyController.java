package com.repairmanagement.repairproject.company;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyView create(@Valid @RequestBody CompanyRequest body, Principal principal) {
        return companyService.create(body, principal.getName());
    }

    @GetMapping
    public List<CompanyView> mine(Principal principal) {
        return companyService.mine(principal.getName());
    }

    @GetMapping("/{companyId}")
    public CompanyView get(@PathVariable Long companyId, Principal principal) {
        return companyService.get(companyId, principal.getName());
    }

    @GetMapping("/{companyId}/members")
    public List<MemberView> members(@PathVariable Long companyId, Principal principal) {
        return companyService.listMembers(companyId, principal.getName());
    }

    @PatchMapping("/{companyId}/members/{userId}/role")
    public MemberView changeRole(@PathVariable Long companyId, @PathVariable Long userId,
                                 @Valid @RequestBody ChangeMemberRoleRequest body, Principal principal) {
        return companyService.changeMemberRole(companyId, userId, body, principal.getName());
    }

    @DeleteMapping("/{companyId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long companyId, @PathVariable Long userId, Principal principal) {
        companyService.removeMember(companyId, userId, principal.getName());
    }

    @PostMapping("/{companyId}/members/{userId}/restore")
    public MemberView restoreMember(@PathVariable Long companyId, @PathVariable Long userId,
                                    @Valid @RequestBody ChangeMemberRoleRequest body, Principal principal) {
        return companyService.restoreMember(companyId, userId, body, principal.getName());
    }

    @PostMapping("/{companyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView addMember(@PathVariable Long companyId,
                                @Valid @RequestBody AddMemberRequest body,
                                Principal principal) {
        return companyService.addMember(companyId, body, principal.getName());
    }
}
