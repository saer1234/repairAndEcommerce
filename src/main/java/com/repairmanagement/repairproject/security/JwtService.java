package com.repairmanagement.repairproject.security;

import com.repairmanagement.repairproject.user.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long durationMinutes;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.audience}") String audience,
                      @Value("${app.jwt.access-token-minutes:30}") long durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 720)
            throw new IllegalArgumentException("JWT lifetime must be 1–720 minutes");
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.durationMinutes = durationMinutes;
    }

    public Token issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(durationMinutes));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getEmail())
            .audience(java.util.List.of(audience)).issuedAt(now).expiresAt(expires)
            .claim("uid", user.getId()).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new Token(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expires);
    }
    public record Token(String value, Instant expiresAt) {}
}
