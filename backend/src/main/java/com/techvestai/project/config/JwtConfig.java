package com.techvestai.project.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final Environment env;
    private String resolvedSecret;

    public JwtConfig(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void validateSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = env.getProperty("spring.security.jwt.secret");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is required and must be at least 32 characters");
        }
        this.resolvedSecret = secret;
        log.info("JWT secret validated successfully");
    }

    @Bean
    public String jwtSecret() {
        return resolvedSecret;
    }
}
