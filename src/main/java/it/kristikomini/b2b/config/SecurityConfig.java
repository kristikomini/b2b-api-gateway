package it.kristikomini.b2b.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The service is an OAuth2 <b>resource server</b>: it validates JWTs (signature via the
 * issuer's JWKS, plus issuer/expiry) and does not issue them. Stateless — no session, no CSRF
 * token — because every request carries its own bearer token.
 *
 * <p>The {@code JwtDecoder} is auto-configured from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (Keycloak), so there is no
 * decoder wiring here to get subtly wrong.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
