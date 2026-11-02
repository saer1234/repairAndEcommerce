package com.repairmanagement.repairproject.user;

import com.repairmanagement.repairproject.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    public LoginService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
    }
    @Transactional(readOnly = true)
    public LoginView login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
            .filter(User::isActive)
            .orElseThrow(this::invalid);
        if (!encoder.matches(request.password(), user.getPasswordHash())) throw invalid();
        JwtService.Token token = jwt.issue(user);
        return new LoginView(token.value(), "Bearer", token.expiresAt(), UserView.of(user));
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
