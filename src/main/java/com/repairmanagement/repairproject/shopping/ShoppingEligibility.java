package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.company.CompanyMemberRepository;
import com.repairmanagement.repairproject.user.PlatformRole;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Only active, standalone CLIENT accounts may shop or check out. */
@Service
public class ShoppingEligibility {
    private final UserService users;
    private final CompanyMemberRepository memberships;

    public ShoppingEligibility(UserService users, CompanyMemberRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    public User requireClient(String email) {
        User user = users.currentUser(email); // reads current account state from MySQL
        if (user.getPlatformRole() != PlatformRole.CLIENT ||
            memberships.existsByUser_IdAndActiveTrue(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only Client accounts without active company roles may shop");
        }
        return user;
    }
}
