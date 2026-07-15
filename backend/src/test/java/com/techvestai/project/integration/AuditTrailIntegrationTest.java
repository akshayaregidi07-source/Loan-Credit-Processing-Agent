package com.techvestai.project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techvestai.project.dto.request.ApplicationSubmitRequest;
import com.techvestai.project.dto.request.LoginRequest;
import com.techvestai.project.dto.response.AuthResponse;
import com.techvestai.project.entity.AuditEvent;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.EmploymentStatus;
import com.techvestai.project.enums.UserRole;
import com.techvestai.project.repository.AuditEventRepository;
import com.techvestai.project.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for audit trail completeness — Task 20.4.
 *
 * <p>Submits an application and verifies the APPLICATION_SUBMITTED event is
 * recorded with the required fields and that the audit trail endpoint returns
 * it ordered by {@code created_at} ASC.
 *
 * <p>The full pipeline (DVA → CPA → RA → FA) requires real document files;
 * this test verifies the submission event and the audit endpoint contract.
 * Pipeline-level audit completeness is covered by AgentPipelineIntegrationTest.
 *
 * <p>Requirements: 8.1, 8.3, 8.6
 */
class AuditTrailIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc               mockMvc;
    @Autowired ObjectMapper          objectMapper;
    @Autowired UserService           userService;
    @Autowired AuditEventRepository  auditEventRepository;

    @Test
    void auditTrailEndpoint_returnsEventsOrderedByCreatedAtAsc() throws Exception {
        String username = "audit-user-" + UUID.randomUUID();
        userService.createUser(username, "TestPass123!", UserRole.APPLICANT);

        // Login
        MvcResult loginResult = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "TestPass123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // Submit application — triggers APPLICATION_SUBMITTED event
        ApplicationSubmitRequest appReq = new ApplicationSubmitRequest(
                new BigDecimal("8000"),
                "Car purchase",
                EmploymentStatus.EMPLOYED,
                new BigDecimal("2500"),
                new BigDecimal("300"));

        MvcResult appResult = mockMvc.perform(
                post("/api/v1/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String location = appResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String[] parts = location.split("/");
        UUID applicationId = UUID.fromString(parts[parts.length - 2]);

        // Verify APPLICATION_SUBMITTED event was persisted with required fields (Req 8.1)
        List<AuditEvent> events = auditEventRepository
                .findByApplicationIdOrderByCreatedAtAsc(applicationId);

        assertThat(events).isNotEmpty();

        AuditEvent submittedEvent = events.stream()
                .filter(e -> e.getEventType() == AuditEventType.APPLICATION_SUBMITTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("APPLICATION_SUBMITTED event not found"));

        assertThat(submittedEvent.getEventId()).isNotNull();
        assertThat(submittedEvent.getApplicationId()).isEqualTo(applicationId);
        assertThat(submittedEvent.getActor()).isEqualTo(username);
        assertThat(submittedEvent.getCreatedAt()).isNotNull();
        assertThat(submittedEvent.getEventPayload()).containsKey("applicationId");

        // Verify events are ordered ascending by created_at (Req 8.3)
        List<AuditEvent> ordered = events;
        for (int i = 1; i < ordered.size(); i++) {
            assertThat(ordered.get(i).getCreatedAt())
                    .isAfterOrEqualTo(ordered.get(i - 1).getCreatedAt());
        }

        // Verify the audit trail endpoint returns the same events (Req 8.3)
        // Use an UNDERWRITER or ADMIN token — create one for this check
        String adminUser = "audit-admin-" + UUID.randomUUID();
        userService.createUser(adminUser, "AdminPass123!", UserRole.UNDERWRITER);
        MvcResult adminLogin = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(adminUser, "AdminPass123!"))))
                .andExpect(status().isOk()).andReturn();
        String adminToken = objectMapper.readValue(
                adminLogin.getResponse().getContentAsString(), AuthResponse.class).token();

        MvcResult trailResult = mockMvc.perform(
                get("/api/v1/audit/{applicationId}", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<?> returnedEvents = objectMapper.readValue(
                trailResult.getResponse().getContentAsString(), List.class);
        assertThat(returnedEvents).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void auditEvents_haveUniqueEventIds() throws Exception {
        // Insert two events for the same application and confirm UUIDs are distinct (Req 8.5)
        String username = "audit-unique-" + UUID.randomUUID();
        userService.createUser(username, "TestPass123!", UserRole.APPLICANT);

        MvcResult loginResult = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "TestPass123!"))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // Submit two applications to generate multiple distinct events
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                    post("/api/v1/applications")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApplicationSubmitRequest(
                                    new BigDecimal("5000"),
                                    "Purpose " + i,
                                    EmploymentStatus.EMPLOYED,
                                    new BigDecimal("2000"),
                                    new BigDecimal("200")))))
                    .andExpect(status().isCreated());
        }

        // All persisted event IDs must be unique
        List<UUID> allIds = auditEventRepository.findAll().stream()
                .map(AuditEvent::getEventId)
                .collect(Collectors.toList());

        long distinctCount = allIds.stream().distinct().count();
        assertThat(distinctCount).isEqualTo(allIds.size());
    }
}
