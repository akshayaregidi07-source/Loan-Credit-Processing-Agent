package com.techvestai.project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techvestai.project.dto.request.LoginRequest;
import com.techvestai.project.dto.response.AuthResponse;
import com.techvestai.project.service.UserService;
import com.techvestai.project.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the JWT authentication flow — Task 20.2.
 *
 * <p>Requirements: 1.1, 1.2, 1.3
 */
class JwtAuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc        mockMvc;
    @Autowired ObjectMapper   objectMapper;
    @Autowired UserService    userService;

    private static final String USERNAME = "auth-test-user";
    private static final String PASSWORD = "TestPass123!";

    @BeforeEach
    void setUp() {
        // Idempotently create the test user
        try {
            userService.createUser(USERNAME, PASSWORD, UserRole.APPLICANT);
        } catch (Exception ignored) {
            // User may already exist from a previous test run in the same container
        }
    }

    /** Successful login returns 200 and a non-blank JWT. */
    @Test
    void login_withValidCredentials_returns200AndToken() throws Exception {
        LoginRequest req = new LoginRequest(USERNAME, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(body.token()).isNotBlank();
        assertThat(body.role()).isEqualTo("ROLE_APPLICANT");
    }

    /** Wrong password returns 401 with no token. */
    @Test
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest(USERNAME, "wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    /** Accessing a protected endpoint without a token returns 401. */
    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());
    }

    /** Accessing a protected endpoint with an expired token returns 401. */
    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        var secretBytes = "integration-test-secret-key-at-least-32-characters-long"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secretBytes);

        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .setSubject(USERNAME)
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/applications")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
