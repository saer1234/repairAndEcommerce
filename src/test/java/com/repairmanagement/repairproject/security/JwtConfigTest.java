package com.repairmanagement.repairproject.security;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
class JwtConfigTest {
  private final JwtConfig cfg = new JwtConfig();
  @Test void requiresAtLeastThirtyTwoBytes() {
    assertThrows(IllegalStateException.class, () -> cfg.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[20])));
  }
  @Test void rejectsMalformedBase64() {
    assertThrows(IllegalStateException.class, () -> cfg.jwtSecretKey("not_a_base64_secret!"));
  }
  @Test void validSecretProducesAKey() {
    assertEquals(32, cfg.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[32])).getEncoded().length);
  }
}
