package com.techvestai.project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techvestai.project.dto.request.ApplicationSubmitRequest;
import com.techvestai.project.dto.request.LoginRequest;
import com.techvestai.project.dto.response.AuthResponse;
import com.techvestai.project.entity.AuditEvent;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.EmploymentStatus;
import com.techvestai.project.enums.UserRole;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.AuditEventRepository;
import com.techvestai.project.repository.CreditScoreRepository;
import com.techvestai.project.repository.FairnessResultRepository;
import com.techvestai.project.repository.RecommendationRepository;
import com.techvestai.project.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full agent pipeline integration test — Task 20.1.
 *
 * <p>Registers a test applicant, submits an application, uploads the three
 * required documents (using minimal valid PDF bytes), then asserts:
 * <ul>
 *   <li>Application status reaches {@code AWAITING_UNDERWRITER_REVIEW}
 *   <li>{@code CreditScore}, {@code Recommendation}, and {@code FairnessResult}
 *       records are persisted
 *   <li>All mandatory audit event types are present
 * </ul>
 *
 * <p>Requirements: 3.7, 4.8, 5.7, 6.7, 8.6
 */
class AgentPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc                  mockMvc;
    @Autowired ObjectMapper             objectMapper;
    @Autowired UserService              userService;
    @Autowired ApplicationRepository    applicationRepository;
    @Autowired CreditScoreRepository    creditScoreRepository;
    @Autowired RecommendationRepository recommendationRepository;
    @Autowired FairnessResultRepository fairnessResultRepository;
    @Autowired AuditEventRepository     auditEventRepository;

    private static final Set<AuditEventType> REQUIRED_EVENT_TYPES = EnumSet.of(
            AuditEventType.APPLICATION_SUBMITTED,
            AuditEventType.DOCUMENT_VALIDATION_RESULT,
            AuditEventType.CREDIT_SCORE_COMPUTED,
            AuditEventType.RECOMMENDATION_PRODUCED,
            AuditEventType.FAIRNESS_EVALUATION_COMPLETED
    );

    @Test
    void fullPipeline_withValidDocuments_reachesAwaitingReview() throws Exception {
        // 1. Create applicant user
        String username = "pipeline-user-" + UUID.randomUUID();
        userService.createUser(username, "TestPass123!", UserRole.APPLICANT);

        // 2. Login and get JWT
        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new LoginRequest(username, "TestPass123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // 3. Submit application
        ApplicationSubmitRequest req = new ApplicationSubmitRequest(
                new BigDecimal("15000"),
                "Home improvement",
                EmploymentStatus.EMPLOYED,
                new BigDecimal("4000"),
                new BigDecimal("600"));

        MvcResult appResult = mockMvc.perform(
                        post("/api/v1/applications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String location = appResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String[] parts = location.split("/");
        UUID applicationId = UUID.fromString(parts[parts.length - 2]);

        // 4. Upload three matching documents (same filename stem ensures consistency check passes)
        byte[] pdfBytes = buildMinimalPdf();
        for (String docType : List.of("GOVERNMENT_ID", "INCOME_PROOF", "BANK_STATEMENT")) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "john_doe.pdf", "application/pdf", pdfBytes);
            mockMvc.perform(
                            multipart("/api/v1/applications/{id}/documents", applicationId)
                                    .file(file)
                                    .param("documentType", docType)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }

        // 5. Assert application status reached AWAITING_UNDERWRITER_REVIEW
        var savedApp = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AssertionError("Application not found: " + applicationId));
        assertThat(savedApp.getStatus())
                .isEqualTo(ApplicationStatus.AWAITING_UNDERWRITER_REVIEW);

        // 6. Assert all three scoring records were persisted
        assertThat(creditScoreRepository.findByApplication_Id(applicationId))
                .as("CreditScore must be persisted after CPA runs").isPresent();
        assertThat(recommendationRepository.findByApplication_Id(applicationId))
                .as("Recommendation must be persisted after RA runs").isPresent();
        assertThat(fairnessResultRepository.findByApplication_Id(applicationId))
                .as("FairnessResult must be persisted after FA runs").isPresent();

        // 7. Assert all mandatory audit event types are present
        List<AuditEvent> events =
                auditEventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        Set<AuditEventType> presentTypes = events.stream()
                .map(AuditEvent::getEventType)
                .collect(Collectors.toSet());
        assertThat(presentTypes)
                .as("All mandatory audit event types must be recorded")
                .containsAll(REQUIRED_EVENT_TYPES);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /** Returns minimal valid PDF bytes (correct magic bytes + legal cross-reference). */
    private byte[] buildMinimalPdf() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj\n"
                + "xref\n0 4\n"
                + "0000000000 65535 f\n"
                + "0000000009 00000 n\n"
                + "0000000058 00000 n\n"
                + "0000000115 00000 n\n"
                + "trailer<</Size 4/Root 1 0 R>>\n"
                + "startxref\n190\n%%EOF";
        return pdf.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
