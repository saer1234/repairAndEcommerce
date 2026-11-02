package com.repairmanagement.repairproject.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

@Configuration
public class JwtConfig {
    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret-base64}") String encoded) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET_BASE64 must be valid Base64", e); }
        if (decoded.length < 32) throw new IllegalStateException("JWT secret must be >= 32 random bytes");
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key,
                                 @Value("${app.jwt.issuer}") String issuer,
                                 @Value("${app.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }
}
