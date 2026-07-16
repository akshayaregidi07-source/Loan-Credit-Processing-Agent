package com.techvestai.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Isolates the {@link PasswordEncoder} bean so it can be injected into
 * {@link com.techvestai.project.service.UserService} without creating a
 * circular dependency through {@link SecurityConfig}.
 *
 * <p>Cycle that this class breaks:
 * <pre>
 *   SecurityConfig → UserDetailsService (UserService)
 *   UserService    → PasswordEncoder
 *   PasswordEncoder @Bean was on SecurityConfig  ← cycle
 * </pre>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
