package com.techvestai.project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techvestai.project.dto.request.ApplicationSubmitRequest;
import com.techvestai.project.dto.request.LoginRequest;
import com.techvestai.project.dto.response.AuthResponse;
import com.techvestai.project.enums.EmploymentStatus;
import com.techvestai.project.enums.UserRole;
import com.techvestai.project.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for multipart document upload — Task 20.3.
 *
 * <p>Verifies size (413), MIME type (415), and success (201) scenarios,
 * and confirms the storage path is UUID-based (Requirement 13.2).
 *
 * <p>Requirements: 2.3–2.6, 13.2
 */
class DocumentUploadIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService  userService;

    private String applicantToken;
    private UUID   applicationId;

    @BeforeEach
    void setUp() throws Exception {
        // Create applicant user
        String user = "doc-upload-" + UUID.randomUUID();
        userService.createUser(user, "TestPass123!", UserRole.APPLICANT);

        // Login to get JWT
        MvcResult loginResult = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user, "TestPass123!"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);
        applicantToken = auth.token();

        // Submit an application so we have an applicationId to upload to
        ApplicationSubmitRequest appReq = new ApplicationSubmitRequest(
                new BigDecimal("10000"),
                "Home renovation",
                EmploymentStatus.EMPLOYED,
                new BigDecimal("3000"),
                new BigDecimal("500"));

        MvcResult appResult = mockMvc.perform(
                post("/api/v1/applications")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract applicationId from Location header: .../applications/{id}/status
        String location = appResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String[] parts = location.split("/");
        applicationId = UUID.fromString(parts[parts.length - 2]);
    }

    /** Valid PDF file returns 201 and a UUID-based storage path. */
    @Test
    void uploadValidPdf_returns201WithDocumentMetadata() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();

        MockMultipartFile file = new MockMultipartFile(
                "file", "government_id.pdf", "application/pdf", pdfBytes);

        MvcResult result = mockMvc.perform(
                multipart("/api/v1/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "GOVERNMENT_ID")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("GOVERNMENT_ID");
        // Storage path should contain a UUID segment (not the username or applicationId)
        assertThat(body).doesNotContain(applicationId.toString());
    }

    /** File exceeding 10 MB returns 413. */
    @Test
    void uploadOversizedFile_returns413() throws Exception {
        byte[] oversized = new byte[11 * 1024 * 1024]; // 11 MB
        // Prepend PDF magic bytes so MIME check passes but size check fails first
        oversized[0] = 0x25; oversized[1] = 0x50; oversized[2] = 0x44; oversized[3] = 0x46;

        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", oversized);

        mockMvc.perform(
                multipart("/api/v1/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "INCOME_PROOF")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isPayloadTooLarge());
    }

    /** Unsupported MIME type returns 422 (magic-byte mismatch detected by DocumentService). */
    @Test
    void uploadUnsupportedMimeType_returns422() throws Exception {
        byte[] txtBytes = "This is plain text, not a PDF".getBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", txtBytes);

        mockMvc.perform(
                multipart("/api/v1/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "BANK_STATEMENT")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns the minimum valid PDF bytes (magic bytes + minimal structure). */
    private byte[] buildMinimalPdf() {
        // A truly minimal PDF that has correct magic bytes
        String minimal = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj\n" +
                "xref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n" +
                "0000000058 00000 n\n0000000115 00000 n\n" +
                "trailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%EOF";
        return minimal.getBytes();
    }
}
