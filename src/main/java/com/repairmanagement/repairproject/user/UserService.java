package com.repairmanagement.repairproject.user;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        // BCrypt accepts at most 72 bytes; max 72 characters alone is not sufficient for UTF-8.
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long in UTF-8");
        }
        User user = new User(request.fullName().trim(), email,
                             passwordEncoder.encode(request.password()), request.phone(),
                             PlatformRole.valueOf(request.accountType().name()));
        try {
            return UserView.of(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public User currentUser(String authenticatedEmail) {
        return users.findByEmailIgnoreCase(authenticatedEmail)
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
    }
}
