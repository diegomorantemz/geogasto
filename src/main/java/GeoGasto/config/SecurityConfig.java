package GeoGasto.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.io.IOException;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // ===== Public =====
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/",
                                "/*.html",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/icons/**",
                                "/favicon.ico",
                                "/favicon.svg",
                                "/manifest.webmanifest",
                                "/sw.js",
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // ===== API REST → JWT =====
                        .requestMatchers("/api/**").authenticated()

                        // ===== Everything else → JWT =====
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Respuestas JSON coherentes: 401 si falta/expira el token, 403 si no tiene permiso
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "No autenticado: token ausente, inválido o expirado"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Acceso denegado"))
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                        "img-src 'self' data: blob: https://cdn.jsdelivr.net https://tile.openstreetmap.org https://*.tile.openstreetmap.org; " +
                                        "font-src 'self' data: https://cdn.jsdelivr.net; " +
                                        "connect-src 'self' https://tile.openstreetmap.org https://*.tile.openstreetmap.org; " +
                                        "worker-src 'self' blob:; child-src 'self' blob:; object-src 'none'; " +
                                        "frame-ancestors 'none'; base-uri 'self'"
                        ))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"message":"%s"}
                """.formatted(LocalDateTime.now(), status, message));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}