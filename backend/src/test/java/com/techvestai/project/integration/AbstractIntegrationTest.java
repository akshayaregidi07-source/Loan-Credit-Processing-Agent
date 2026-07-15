package com.techvestai.project.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for all integration tests.
 *
 * <p>Spins up a single PostgreSQL container per test-class lifecycle
 * ({@code @Container} is static) and wires Spring's datasource properties
 * via {@link DynamicPropertySource}.
 *
 * <p>Flyway migrations run automatically on context startup, creating the full
 * schema in the Testcontainers database. Each test class gets a fresh
 * application context that is shared within the class to keep suite time low.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("techvest_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Use a fixed JWT secret for tests (≥ 32 chars, no env var required)
        registry.add("spring.security.jwt.secret",
                () -> "integration-test-secret-key-at-least-32-characters-long");
    }
}
