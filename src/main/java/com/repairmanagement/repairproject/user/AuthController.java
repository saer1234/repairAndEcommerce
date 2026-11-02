package com.repairmanagement.repairproject.user;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService userService;
    private final LoginService loginService;
    public AuthController(UserService userService, LoginService loginService) {
        this.userService = userService; this.loginService = loginService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(loginService.login(request));
    }

    @GetMapping("/me")
    public UserView me(Principal principal) {
        return UserView.of(userService.currentUser(principal.getName()));
    }
}
