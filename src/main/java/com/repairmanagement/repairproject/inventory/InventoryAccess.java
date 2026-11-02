package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InventoryAccess {
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final UserService users;

    public InventoryAccess(CompanyRepository companies, CompanyMemberRepository members, UserService users) {
        this.companies = companies;
        this.members = members;
        this.users = users;
    }

    public Company requireActiveCompany(Long companyId) {
        return companies.findById(companyId)
            .filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    public User requireMember(Long companyId, String email) {
        User user = users.currentUser(email);
        CompanyMember membership = members.findByCompany_IdAndUser_IdAndActiveTrue(companyId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        requireActiveCompany(companyId);
        return user;
    }

    public User requireManager(Long companyId, String email) {
        User user = requireMember(companyId, email);
        CompanyMember membership = members.findByCompany_IdAndUser_IdAndActiveTrue(companyId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        if (membership.getMemberRole() != MemberRole.OWNER && membership.getMemberRole() != MemberRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company manager permission required");
        }
        return user;
    }
}
