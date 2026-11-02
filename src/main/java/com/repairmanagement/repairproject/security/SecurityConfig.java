package com.repairmanagement.repairproject.security;

import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserRepository;
import java.util.List;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins:http://localhost:4200}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Always load live roles and active flag from MySQL: roles stored in JWTs can go stale.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            User user = users.findByEmailIgnoreCase(jwt.getSubject()).filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Account not available"));
            Object uid = jwt.getClaim("uid");
            if (!(uid instanceof Number) || ((Number)uid).longValue() != user.getId()) {
                throw new BadCredentialsException("Account mismatch");
            }
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name()));
        });
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/catalog/companies", "/api/companies/*/items", "/api/companies/*/items/*", "/api/companies/*/items/*/image").permitAll()
                // URL gate + service-layer membership check are both required.
                .requestMatchers("/api/companies/*/cart", "/api/companies/*/cart/**").hasRole("CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/companies/*/checkout").hasRole("CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/companies").hasAnyRole("OWNER", "SUPER_ADMIN")
                .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
