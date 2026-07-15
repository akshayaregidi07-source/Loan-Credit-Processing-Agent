package com.techvestai.project.agent;

import com.techvestai.project.entity.Application;
import com.techvestai.project.entity.Document;
import com.techvestai.project.entity.DocumentExtractionPayload;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.DocumentType;
import com.techvestai.project.enums.DocumentValidationStatus;
import com.techvestai.project.exception.DocumentValidationException;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.DocumentExtractionPayloadRepository;
import com.techvestai.project.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document Validation Agent — Tasks 7.1, 7.2, 7.3.
 *
 * <p>Executes three sequential validation steps for a given application:
 * <ol>
 *   <li><b>Presence check</b> — all three required document types are uploaded.
 *   <li><b>Integrity check</b> — each file is readable, non-empty, and its
 *       magic bytes match the declared MIME type.
 *   <li><b>Cross-document consistency</b> — applicant name on Government-issued
 *       ID matches the name on Income Proof.
 * </ol>
 *
 * <p>On any failure the application status is updated, an audit event is
 * recorded, and a {@link DocumentValidationException} halts the pipeline.
 * On full pass, extraction data is persisted and the
 * {@link CreditPolicyAgent} is triggered.
 *
 * <p><b>Requirements:</b> 3.1–3.8, 12.1
 */
@Component
public class DocumentValidationAgent {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationAgent.class);

    // Magic-byte signatures for supported MIME types
    private static final byte[] PDF_MAGIC   = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] JPEG_MAGIC  = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] PNG_MAGIC   = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47}; // ‌PNG

    private static final List<DocumentType> REQUIRED_TYPES = List.of(
            DocumentType.GOVERNMENT_ID,
            DocumentType.INCOME_PROOF,
            DocumentType.BANK_STATEMENT
    );

    private final ApplicationRepository         applicationRepository;
    private final DocumentRepository            documentRepository;
    private final DocumentExtractionPayloadRepository extractionPayloadRepository;
    private final AuditAgent                    auditAgent;
    private final DocumentParser                documentParser;
    private final CreditPolicyAgent             creditPolicyAgent;

    @Value("${app.document.storage-base-dir:./uploads}")
    private String storageBaseDir;

    public DocumentValidationAgent(ApplicationRepository applicationRepository,
                                   DocumentRepository documentRepository,
                                   DocumentExtractionPayloadRepository extractionPayloadRepository,
                                   AuditAgent auditAgent,
                                   DocumentParser documentParser,
                                   @Lazy CreditPolicyAgent creditPolicyAgent) {
        this.applicationRepository      = applicationRepository;
        this.documentRepository         = documentRepository;
        this.extractionPayloadRepository = extractionPayloadRepository;
        this.auditAgent                 = auditAgent;
        this.documentParser             = documentParser;
        this.creditPolicyAgent          = creditPolicyAgent;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Runs all three validation steps for the given application.
     *
     * <p>Called by {@code ApplicationService} immediately after an application
     * is persisted with status {@code SUBMITTED}.
     *
     * @param applicationId the application to validate
     * @throws DocumentValidationException if any validation step fails
     */
    @Transactional
    public void validate(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Application not found: " + applicationId));

        log.info("DocumentValidationAgent starting for application {}", applicationId);

        // Step 1 — presence check
        Map<DocumentType, Document> documentMap = checkPresence(application);

        // Step 2 — integrity check
        checkIntegrity(application, documentMap);

        // Step 3 — cross-document consistency + extraction
        extractAndPersist(application, documentMap);

        // All checks passed — trigger Credit Policy Agent
        log.info("DocumentValidationAgent completed successfully for application {}", applicationId);
        creditPolicyAgent.score(applicationId);
    }

    // -----------------------------------------------------------------------
    // Step 1: Presence check (Task 7.1)
    // -----------------------------------------------------------------------

    private Map<DocumentType, Document> checkPresence(Application application) {
        UUID appId = application.getId();
        List<Document> docs = documentRepository.findByApplication_Id(appId);

        Map<DocumentType, Document> docMap = new LinkedHashMap<>();
        for (Document d : docs) {
            docMap.put(d.getDocumentType(), d);
        }

        List<String> missing = new ArrayList<>();
        for (DocumentType required : REQUIRED_TYPES) {
            if (!docMap.containsKey(required)) {
                missing.add(required.name());
            }
        }

        if (!missing.isEmpty()) {
            application.setStatus(ApplicationStatus.DOCUMENT_INCOMPLETE);
            applicationRepository.save(application);

            auditAgent.recordEvent(
                    AuditEventType.DOCUMENT_VALIDATION_RESULT,
                    appId,
                    "DocumentValidationAgent",
                    Map.of(
                            "step", "PRESENCE_CHECK",
                            "outcome", "FAIL",
                            "missingDocumentTypes", missing
                    )
            );
            throw new DocumentValidationException(
                    "Missing required document types: " + missing);
        }

        auditAgent.recordEvent(
                AuditEventType.DOCUMENT_VALIDATION_RESULT,
                appId,
                "DocumentValidationAgent",
                Map.of("step", "PRESENCE_CHECK", "outcome", "PASS")
        );
        return docMap;
    }

    // -----------------------------------------------------------------------
    // Step 2: Integrity check (Task 7.2)
    // -----------------------------------------------------------------------

    private void checkIntegrity(Application application,
                                 Map<DocumentType, Document> documentMap) {
        UUID appId = application.getId();

        for (Document doc : documentMap.values()) {
            String failureReason = validateFileIntegrity(doc);
            if (failureReason != null) {
                doc.setValidationStatus(DocumentValidationStatus.FAILED);
                doc.setValidationFailureReason(failureReason);
                documentRepository.save(doc);

                application.setStatus(ApplicationStatus.DOCUMENT_INVALID);
                applicationRepository.save(application);

                auditAgent.recordEvent(
                        AuditEventType.DOCUMENT_VALIDATION_RESULT,
                        appId,
                        "DocumentValidationAgent",
                        Map.of(
                                "step", "INTEGRITY_CHECK",
                                "outcome", "FAIL",
                                "documentId", doc.getId().toString(),
                                "documentType", doc.getDocumentType().name(),
                                "failureReason", failureReason
                        )
                );
                throw new DocumentValidationException(
                        "Document integrity check failed: " + failureReason,
                        doc.getId().toString());
            }

            doc.setValidationStatus(DocumentValidationStatus.PASSED);
            documentRepository.save(doc);
        }

        auditAgent.recordEvent(
                AuditEventType.DOCUMENT_VALIDATION_RESULT,
                appId,
                "DocumentValidationAgent",
                Map.of("step", "INTEGRITY_CHECK", "outcome", "PASS")
        );
    }

    /**
     * Returns a non-null failure reason string if the document fails integrity
     * validation, or {@code null} if the document passes.
     */
    private String validateFileIntegrity(Document doc) {
        if (doc.getFileSizeBytes() == null || doc.getFileSizeBytes() <= 0) {
            return "File size is zero or unknown";
        }

        Path filePath = Paths.get(storageBaseDir, doc.getStoragePath());
        if (!Files.exists(filePath)) {
            return "File not found at storage path: " + doc.getStoragePath();
        }

        // Magic-byte check
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = is.readNBytes(4);
            String mimeType = doc.getMimeType();

            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                if (!startsWith(header, PDF_MAGIC)) {
                    return "File does not have a valid PDF signature";
                }
            } else if ("image/jpeg".equalsIgnoreCase(mimeType)) {
                if (!startsWith(header, JPEG_MAGIC)) {
                    return "File does not have a valid JPEG signature";
                }
            } else if ("image/png".equalsIgnoreCase(mimeType)) {
                if (!startsWith(header, PNG_MAGIC)) {
                    return "File does not have a valid PNG signature";
                }
            } else {
                return "Unsupported MIME type: " + mimeType;
            }
        } catch (IOException e) {
            return "File could not be read: " + e.getMessage();
        }

        return null; // passes
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        return Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    // -----------------------------------------------------------------------
    // Step 3: Cross-document consistency + extraction (Task 7.3)
    // -----------------------------------------------------------------------

    private void extractAndPersist(Application application,
                                    Map<DocumentType, Document> documentMap) {
        UUID appId = application.getId();

        Document govId      = documentMap.get(DocumentType.GOVERNMENT_ID);
        Document incomeProof = documentMap.get(DocumentType.INCOME_PROOF);
        Document bankStmt   = documentMap.get(DocumentType.BANK_STATEMENT);

        String nameFromId     = documentParser.extractApplicantName(govId);
        String nameFromIncome = documentParser.extractApplicantName(incomeProof);

        // Normalise before comparison (lowercase, trim)
        String normId     = nameFromId     == null ? "" : nameFromId.trim().toLowerCase();
        String normIncome = nameFromIncome == null ? "" : nameFromIncome.trim().toLowerCase();

        if (!normId.equals(normIncome)) {
            application.setStatus(ApplicationStatus.DOCUMENT_INCONSISTENT);
            applicationRepository.save(application);

            auditAgent.recordEvent(
                    AuditEventType.DOCUMENT_VALIDATION_RESULT,
                    appId,
                    "DocumentValidationAgent",
                    Map.of(
                            "step", "CONSISTENCY_CHECK",
                            "outcome", "FAIL",
                            "finding", "CONSISTENCY_MISMATCH",
                            "nameOnId", normId,
                            "nameOnIncome", normIncome
                    )
            );
            throw new DocumentValidationException(
                    "Applicant name on Income Proof does not match Government-issued ID");
        }

        // ---- Extract fields and build the JSONB payload ----
        String dobStr              = documentParser.extractDateOfBirth(govId);
        int    incomeMonths        = documentParser.extractConsecutiveIncomeMonths(bankStmt);
        double repaymentRatio      = documentParser.extractOnTimeRepaymentRatio(bankStmt);

        // grossMonthlyIncome: prefer parser value; fall back to application field
        String incomeStr = documentParser.extractGrossMonthlyIncome(incomeProof);
        String grossIncome = (incomeStr != null && !incomeStr.isBlank())
                ? incomeStr
                : application.getGrossMonthlyIncome().toPlainString();

        Map<String, Object> fields = new HashMap<>();
        fields.put("applicantName",          normId);
        fields.put("dateOfBirth",            dobStr);
        fields.put("grossMonthlyIncome",     grossIncome);
        fields.put("consecutiveIncomeMonths", incomeMonths);
        fields.put("onTimeRepaymentRatio",   repaymentRatio);
        fields.put("extractedAt",            Instant.now().toString());

        DocumentExtractionPayload payload = new DocumentExtractionPayload();
        payload.setApplication(application);
        payload.setExtractedFields(fields);
        payload.setExtractionStatus("COMPLETED");
        extractionPayloadRepository.save(payload);

        // Update application status
        application.setStatus(ApplicationStatus.DOCUMENTS_VERIFIED);
        applicationRepository.save(application);

        auditAgent.recordEvent(
                AuditEventType.DOCUMENT_VALIDATION_RESULT,
                appId,
                "DocumentValidationAgent",
                Map.of(
                        "step", "CONSISTENCY_CHECK",
                        "outcome", "PASS",
                        "extractionStatus", "COMPLETED"
                )
        );

        log.info("Documents verified and extraction payload persisted for application {}", appId);
    }
}
