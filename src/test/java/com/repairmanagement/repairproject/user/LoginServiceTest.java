package com.repairmanagement.repairproject.user;
import com.repairmanagement.repairproject.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
class LoginServiceTest {
  private final UserRepository repo = mock(UserRepository.class);
  private final PasswordEncoder pass = mock(PasswordEncoder.class);
  private final JwtService jwt = mock(JwtService.class);
  private final LoginService service = new LoginService(repo, pass, jwt);
  @Test void invalidAccountCannotLogIn() {
    when(repo.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
    assertThrows(ResponseStatusException.class, () -> service.login(new LoginRequest("missing@example.com", "wrong")));
    verifyNoInteractions(jwt);
  }
  @Test void wrongPasswordCannotLogIn() {
    User user = new User("Test", "test@example.com", "hash", null);
    when(repo.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(pass.matches("wrong", "hash")).thenReturn(false);
    assertThrows(ResponseStatusException.class, () -> service.login(new LoginRequest("test@example.com", "wrong")));
    verifyNoInteractions(jwt);
  }
  @Test void validPasswordGetsBearerToken() {
    User user = new User("Test", "test@example.com", "hash", null);
    when(repo.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(pass.matches("correct", "hash")).thenReturn(true);
    when(jwt.issue(user)).thenReturn(new JwtService.Token("signed.token.value", Instant.parse("2030-01-01T00:00:00Z")));
    LoginView view = service.login(new LoginRequest("test@example.com", "correct"));
    assertEquals("Bearer", view.tokenType());
    assertEquals("signed.token.value", view.accessToken());
    assertEquals("test@example.com", view.user().email());
  }
}
