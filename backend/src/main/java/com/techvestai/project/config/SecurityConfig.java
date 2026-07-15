package com.techvestai.project.config;

import com.techvestai.project.security.JwtAuthenticationFilter;
import com.techvestai.project.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * <p>Enforces the RBAC matrix from the design document:
 * <ul>
 *   <li>APPLICANT  — submit applications, upload documents, own status
 *   <li>UNDERWRITER — review applications, submit decisions, read policies, read audit
 *   <li>ADMIN       — user management, policy management, audit export, all UNDERWRITER endpoints
 * </ul>
 * JWT is stateless; sessions are never created.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // Public — authentication endpoint
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Swagger / OpenAPI UI (development convenience)
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**").permitAll()

                // APPLICANT — submit application
                .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasRole("APPLICANT")

                // APPLICANT — upload documents for own application
                .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/documents").hasRole("APPLICANT")

                // APPLICANT — check own application status
                .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/status").hasRole("APPLICANT")

                // UNDERWRITER — list all applications, review detail
                .requestMatchers(HttpMethod.GET, "/api/v1/applications").hasAnyRole("UNDERWRITER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/review").hasAnyRole("UNDERWRITER", "ADMIN")

                // UNDERWRITER — submit final decision
                .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/decision").hasRole("UNDERWRITER")

                // UNDERWRITER + ADMIN — read policies
                .requestMatchers(HttpMethod.GET, "/api/v1/policies").hasAnyRole("UNDERWRITER", "ADMIN")

                // ADMIN — create policy thresholds
                .requestMatchers(HttpMethod.POST, "/api/v1/policies").hasRole("ADMIN")

                // UNDERWRITER + ADMIN — read audit trail
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/**").hasAnyRole("UNDERWRITER", "ADMIN")

                // ADMIN — export audit and user management
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
