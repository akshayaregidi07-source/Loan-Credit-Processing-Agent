package com.techvestai.project.service;

import com.techvestai.project.agent.AuditAgent;
import com.techvestai.project.agent.DocumentValidationAgent;
import com.techvestai.project.dto.request.ApplicationSubmitRequest;
import com.techvestai.project.dto.request.UnderwriterDecisionRequest;
import com.techvestai.project.dto.response.*;
import com.techvestai.project.entity.*;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.FairnessOutcome;
import com.techvestai.project.exception.UnauthorisedResourceException;
import com.techvestai.project.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core application lifecycle service — Task 12.1.
 *
 * <p>Handles submission, status queries, underwriter review retrieval,
 * decision recording, and worklist pagination. The agent pipeline is
 * triggered synchronously after submission; async promotion is a future
 * enhancement.
 *
 * <p><b>Requirements:</b> 2.1, 7.1–7.7, 10.1, 10.3–10.5
 */
@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository           applicationRepository;
    private final DocumentRepository              documentRepository;
    private final CreditScoreRepository           creditScoreRepository;
    private final RecommendationRepository        recommendationRepository;
    private final FairnessResultRepository        fairnessResultRepository;
    private final UnderwriterDecisionRepository   underwriterDecisionRepository;
    private final AuditAgent                      auditAgent;
    private final DocumentValidationAgent         documentValidationAgent;

    /**
     * {@code @Lazy} on {@link DocumentValidationAgent} breaks the
     * ApplicationService → DVA → CPA → RA → FA bean-creation cycle.
     */
    public ApplicationService(ApplicationRepository applicationRepository,
                               DocumentRepository documentRepository,
                               CreditScoreRepository creditScoreRepository,
                               RecommendationRepository recommendationRepository,
                               FairnessResultRepository fairnessResultRepository,
                               UnderwriterDecisionRepository underwriterDecisionRepository,
                               AuditAgent auditAgent,
                               @Lazy DocumentValidationAgent documentValidationAgent) {
        this.applicationRepository         = applicationRepository;
        this.documentRepository            = documentRepository;
        this.creditScoreRepository         = creditScoreRepository;
        this.recommendationRepository      = recommendationRepository;
        this.fairnessResultRepository      = fairnessResultRepository;
        this.underwriterDecisionRepository = underwriterDecisionRepository;
        this.auditAgent                    = auditAgent;
        this.documentValidationAgent       = documentValidationAgent;
    }

    // -----------------------------------------------------------------------
    // Submission (Requirement 2.1, 2.7)
    // -----------------------------------------------------------------------

    /**
     * Creates a new Application with status SUBMITTED, records the
     * APPLICATION_SUBMITTED audit event, then synchronously triggers the
     * Document Validation Agent.
     *
     * @param request   the validated form data
     * @param applicant the authenticated applicant user
     * @return the persisted application ID
     */
    @Transactional
    public UUID submitApplication(ApplicationSubmitRequest request, User applicant) {
        Application application = new Application();
        application.setApplicant(applicant);
        application.setRequestedAmount(request.requestedAmount());
        application.setLoanPurpose(request.loanPurpose());
        application.setEmploymentStatus(request.employmentStatus());
        application.setGrossMonthlyIncome(request.grossMonthlyIncome());
        application.setTotalMonthlyDebt(request.totalMonthlyDebt());
        application.setStatus(ApplicationStatus.SUBMITTED);

        application = applicationRepository.save(application);
        UUID applicationId = application.getId();

        // Audit: APPLICATION_SUBMITTED
        Map<String, Object> payload = new HashMap<>();
        payload.put("applicationId",  applicationId.toString());
        payload.put("applicantId",    applicant.getId().toString());
        payload.put("requestedAmount", request.requestedAmount().toPlainString());
        auditAgent.recordEvent(
                AuditEventType.APPLICATION_SUBMITTED,
                applicationId,
                applicant.getUsername(),
                payload
        );

        // Trigger agent pipeline synchronously
        documentValidationAgent.validate(applicationId);

        return applicationId;
    }

    // -----------------------------------------------------------------------
    // Applicant status view (Requirements 10.1–10.5)
    // -----------------------------------------------------------------------

    /**
     * Returns the current status of an application, enforcing ownership.
     * Never exposes credit score, fairness details, or underwriter justification
     * to the applicant (Requirement 10.5).
     *
     * @throws UnauthorisedResourceException (→ HTTP 404) if the application
     *         does not belong to the requester
     */
    public ApplicationStatusResponse getStatusForApplicant(UUID applicationId, User requester) {
        Application application = applicationRepository
                .findByIdAndApplicantId(applicationId, requester.getId())
                .orElseThrow(() -> new UnauthorisedResourceException(
                        "Application not found: " + applicationId));

        // When DECISION_RECORDED, include decision value + timestamp (Requirement 10.4)
        if (application.getStatus() == ApplicationStatus.DECISION_RECORDED) {
            return underwriterDecisionRepository
                    .findByApplication_Id(applicationId)
                    .map(d -> new ApplicationStatusResponse(
                            applicationId,
                            application.getStatus(),
                            application.getUpdatedAt(),
                            d.getDecisionValue(),
                            d.getDecidedAt()))
                    .orElse(new ApplicationStatusResponse(
                            applicationId,
                            application.getStatus(),
                            application.getUpdatedAt(),
                            null, null));
        }

        return new ApplicationStatusResponse(
                applicationId,
                application.getStatus(),
                application.getUpdatedAt(),
                null, null);
    }

    // -----------------------------------------------------------------------
    // Underwriter review (Requirements 7.1, 7.2)
    // -----------------------------------------------------------------------

    /**
     * Returns the full review payload for an Underwriter or Admin.
     * Includes all agent outputs and surfaces the fairness flag when present
     * (Requirement 6.8).
     */
    public ApplicationReviewResponse getReviewForUnderwriter(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new UnauthorisedResourceException(
                        "Application not found: " + applicationId));

        ApplicationFormData formData = new ApplicationFormData(
                application.getRequestedAmount(),
                application.getLoanPurpose(),
                application.getEmploymentStatus(),
                application.getGrossMonthlyIncome(),
                application.getTotalMonthlyDebt()
        );

        List<DocumentMetadata> documents = documentRepository
                .findByApplication_Id(applicationId).stream()
                .map(d -> new DocumentMetadata(
                        d.getId(),
                        d.getDocumentType(),
                        d.getOriginalFilename(),
                        d.getMimeType(),
                        d.getFileSizeBytes(),
                        d.getUploadedAt(),
                        d.getValidationStatus()))
                .collect(Collectors.toList());

        CreditScoreBreakdown creditScoreBreakdown = creditScoreRepository
                .findByApplication_Id(applicationId)
                .map(cs -> new CreditScoreBreakdown(
                        cs.getCreditScore(),
                        cs.getDtiRatio(),
                        cs.getDtiSubScore(),
                        cs.getIncomeStabilityScore(),
                        cs.getCreditHistoryScore(),
                        cs.getDtiWeight(),
                        cs.getIncomeStabilityWeight(),
                        cs.getCreditHistoryWeight(),
                        cs.getPolicyThreshold().getId(),
                        cs.getComputedAt()))
                .orElse(null);

        RecommendationDetail recommendationDetail = recommendationRepository
                .findByApplication_Id(applicationId)
                .map(r -> new RecommendationDetail(
                        r.getRecommendationValue(),
                        r.getPolicyThreshold().getId(),
                        r.getExplanation(),
                        r.getProducedAt()))
                .orElse(null);

        FairnessResult fr = fairnessResultRepository
                .findByApplication_Id(applicationId).orElse(null);

        FairnessResultResponse fairnessResponse = fr == null ? null
                : new FairnessResultResponse(
                        fr.getOriginalCreditScore(),
                        fr.getAnonymisedCreditScore(),
                        fr.getFairnessDelta(),
                        fr.getFairnessOutcome(),
                        fr.getFlagReason(),
                        fr.getEvaluatedAt());

        boolean hasFairnessFlag = fr != null
                && fr.getFairnessOutcome() == FairnessOutcome.FAIRNESS_FLAG;
        String fairnessFlagReason = hasFairnessFlag ? fr.getFlagReason() : null;

        return new ApplicationReviewResponse(
                applicationId,
                application.getStatus(),
                formData,
                documents,
                creditScoreBreakdown,
                recommendationDetail,
                fairnessResponse,
                hasFairnessFlag,
                fairnessFlagReason,
                application.getCreatedAt()
        );
    }

    // -----------------------------------------------------------------------
    // Decision recording (Requirements 7.3–7.7)
    // -----------------------------------------------------------------------

    /**
     * Records the Underwriter's final decision. Only permitted when the
     * application is in AWAITING_UNDERWRITER_REVIEW status.
     * Once recorded, the application is immutable (Requirement 7.7).
     *
     * @throws IllegalStateException if the application is not in the correct state
     */
    @Transactional
    public void recordDecision(UUID applicationId,
                               UnderwriterDecisionRequest request,
                               User underwriter) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new UnauthorisedResourceException(
                        "Application not found: " + applicationId));

        if (application.getStatus() == ApplicationStatus.DECISION_RECORDED) {
            throw new IllegalStateException(
                    "Decision has already been recorded for application " + applicationId);
        }
        if (application.getStatus() != ApplicationStatus.AWAITING_UNDERWRITER_REVIEW) {
            throw new IllegalStateException(
                    "Application " + applicationId + " is not awaiting underwriter review");
        }

        // Retrieve the system recommendation for the audit record
        com.techvestai.project.enums.RecommendationValue systemRec =
                recommendationRepository.findByApplication_Id(applicationId)
                        .map(r -> r.getRecommendationValue())
                        .orElse(null);

        // Persist the decision atomically
        UnderwriterDecision decision = new UnderwriterDecision();
        decision.setApplication(application);
        decision.setUnderwriter(underwriter);
        decision.setDecisionValue(request.decisionValue());
        decision.setJustificationText(request.justificationText());
        decision.setOverrideReason(request.overrideReason());
        decision.setSystemRecommendation(systemRec);
        underwriterDecisionRepository.save(decision);

        application.setStatus(ApplicationStatus.DECISION_RECORDED);
        applicationRepository.save(application);

        // Audit: FINAL_DECISION_RECORDED
        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("decisionValue",       request.decisionValue().name());
        auditPayload.put("justificationText",   truncate(request.justificationText(), 500));
        auditPayload.put("overrideReason",      request.overrideReason());
        auditPayload.put("underwriterId",       underwriter.getId().toString());
        auditPayload.put("systemRecommendation", systemRec != null ? systemRec.name() : "NONE");
        auditAgent.recordEvent(
                AuditEventType.FINAL_DECISION_RECORDED,
                applicationId,
                underwriter.getUsername(),
                auditPayload
        );
    }

    // -----------------------------------------------------------------------
    // Underwriter worklist (Requirement 7.1)
    // -----------------------------------------------------------------------

    /**
     * Returns a paginated list of applications in AWAITING_UNDERWRITER_REVIEW.
     */
    public Page<ApplicationSummaryResponse> listApplicationsForUnderwriter(Pageable pageable) {
        return applicationRepository
                .findAllByStatus(ApplicationStatus.AWAITING_UNDERWRITER_REVIEW, pageable)
                .map(a -> new ApplicationSummaryResponse(
                        a.getId(),
                        a.getStatus(),
                        a.getRequestedAmount(),
                        a.getLoanPurpose(),
                        a.getCreatedAt(),
                        a.getUpdatedAt()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
