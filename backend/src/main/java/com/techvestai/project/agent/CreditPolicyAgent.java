package com.techvestai.project.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techvestai.project.entity.Application;
import com.techvestai.project.entity.CreditScore;
import com.techvestai.project.entity.DocumentExtractionPayload;
import com.techvestai.project.entity.PolicyThreshold;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.PolicyThresholdStatus;
import com.techvestai.project.exception.ScoringException;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.CreditScoreRepository;
import com.techvestai.project.repository.DocumentExtractionPayloadRepository;
import com.techvestai.project.repository.PolicyThresholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Credit Policy Agent — Tasks 8.1, 8.3, 8.5.
 *
 * <p>Computes a transparent, rules-based Credit Score from the verified
 * financial data stored in {@code document_extraction_payloads}.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Deserialise the extraction payload (fails → SCORING_ERROR / PAYLOAD_DESERIALISATION_FAILURE)
 *   <li>Guard zero income (fails → SCORING_ERROR / ZERO_INCOME)
 *   <li>Compute DTI ratio
 *   <li>Map each factor to a sub-score via the configured band tables
 *   <li>Compute weighted Credit Score: (DTI×0.40 + IncomeStability×0.35 + CreditHistory×0.25) × 10
 *   <li>Persist {@link CreditScore} and update application status to PROCESSING
 *   <li>Record {@code CREDIT_SCORE_COMPUTED} audit event
 *   <li>Trigger {@link RecommendationAgent}
 * </ol>
 *
 * <p>All scoring computation methods are package-private so that property-based
 * tests can exercise them directly without a Spring context.
 *
 * <p><b>Requirements:</b> 4.1–4.8, 12.2, 12.4, 12.5
 */
@Component
public class CreditPolicyAgent {

    private static final Logger log = LoggerFactory.getLogger(CreditPolicyAgent.class);

    // Fixed weights per design.md
    static final BigDecimal DTI_WEIGHT              = new BigDecimal("0.40");
    static final BigDecimal INCOME_STABILITY_WEIGHT = new BigDecimal("0.35");
    static final BigDecimal CREDIT_HISTORY_WEIGHT   = new BigDecimal("0.25");

    private final ApplicationRepository              applicationRepository;
    private final DocumentExtractionPayloadRepository extractionPayloadRepository;
    private final CreditScoreRepository              creditScoreRepository;
    private final PolicyThresholdRepository          policyThresholdRepository;
    private final AuditAgent                         auditAgent;
    private final ObjectMapper                       objectMapper;
    private final RecommendationAgent                recommendationAgent;

    /**
     * {@code @Lazy} on {@link RecommendationAgent} breaks the DVA→CPA→RA
     * circular-dependency chain at the Spring context level.
     */
    public CreditPolicyAgent(ApplicationRepository applicationRepository,
                             DocumentExtractionPayloadRepository extractionPayloadRepository,
                             CreditScoreRepository creditScoreRepository,
                             PolicyThresholdRepository policyThresholdRepository,
                             AuditAgent auditAgent,
                             ObjectMapper objectMapper,
                             @Lazy RecommendationAgent recommendationAgent) {
        this.applicationRepository       = applicationRepository;
        this.extractionPayloadRepository  = extractionPayloadRepository;
        this.creditScoreRepository        = creditScoreRepository;
        this.policyThresholdRepository    = policyThresholdRepository;
        this.auditAgent                   = auditAgent;
        this.objectMapper                 = objectMapper;
        this.recommendationAgent          = recommendationAgent;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Scores the application identified by {@code applicationId}.
     * Called by {@link DocumentValidationAgent} after all documents pass.
     *
     * @param applicationId the application to score
     * @throws ScoringException if income is zero or the payload cannot be deserialised
     */
    @Transactional
    public void score(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Application not found: " + applicationId));

        log.info("CreditPolicyAgent starting for application {}", applicationId);

        // ---- Task 8.1: deserialise extraction payload ----
        ExtractionData data = deserialisePayload(application);

        // ---- Task 8.1: guard zero income ----
        guardZeroIncome(application, data.grossMonthlyIncome);

        // ---- Task 8.1: compute DTI ----
        BigDecimal dtiRatio = data.grossMonthlyIncome.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : data.totalMonthlyDebt.divide(data.grossMonthlyIncome, 2, RoundingMode.HALF_UP);

        // ---- Task 8.3: sub-score band mapping ----
        int dtiSubScore           = computeDtiSubScore(dtiRatio);
        int incomeStabilityScore  = computeIncomeStabilityScore(data.consecutiveIncomeMonths);
        int creditHistoryScore    = computeCreditHistoryScore(data.onTimeRepaymentRatio);

        // ---- Task 8.5: weighted formula ----
        BigDecimal creditScore = computeCreditScore(dtiSubScore, incomeStabilityScore, creditHistoryScore);

        // ---- Task 8.5: fetch active policy threshold ----
        PolicyThreshold activePolicy = policyThresholdRepository
                .findByStatus(PolicyThresholdStatus.ACTIVE)
                .orElseThrow(() -> new ScoringException(
                        "No active policy threshold found", "NO_ACTIVE_POLICY"));

        // ---- Task 8.5: persist CreditScore entity ----
        CreditScore creditScoreEntity = new CreditScore();
        creditScoreEntity.setApplication(application);
        creditScoreEntity.setCreditScore(creditScore);
        creditScoreEntity.setDtiRatio(dtiRatio);
        creditScoreEntity.setDtiSubScore(dtiSubScore);
        creditScoreEntity.setIncomeStabilityScore(incomeStabilityScore);
        creditScoreEntity.setCreditHistoryScore(creditHistoryScore);
        creditScoreEntity.setDtiWeight(DTI_WEIGHT);
        creditScoreEntity.setIncomeStabilityWeight(INCOME_STABILITY_WEIGHT);
        creditScoreEntity.setCreditHistoryWeight(CREDIT_HISTORY_WEIGHT);
        creditScoreEntity.setPolicyThreshold(activePolicy);
        creditScoreRepository.save(creditScoreEntity);

        // ---- Task 8.5: update application status ----
        application.setStatus(ApplicationStatus.PROCESSING);
        applicationRepository.save(application);

        // ---- Task 8.5: audit event ----
        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("creditScore",            creditScore.toPlainString());
        auditPayload.put("dtiRatio",               dtiRatio.toPlainString());
        auditPayload.put("dtiSubScore",            dtiSubScore);
        auditPayload.put("incomeStabilityScore",   incomeStabilityScore);
        auditPayload.put("creditHistoryScore",     creditHistoryScore);
        auditPayload.put("dtiWeight",              DTI_WEIGHT.toPlainString());
        auditPayload.put("incomeStabilityWeight",  INCOME_STABILITY_WEIGHT.toPlainString());
        auditPayload.put("creditHistoryWeight",    CREDIT_HISTORY_WEIGHT.toPlainString());
        auditPayload.put("policyThresholdId",      activePolicy.getId().toString());

        auditAgent.recordEvent(
                AuditEventType.CREDIT_SCORE_COMPUTED,
                applicationId,
                "CreditPolicyAgent",
                auditPayload
        );

        log.info("CreditPolicyAgent scored application {} → creditScore={}", applicationId, creditScore);

        // ---- Task 8.5: trigger Recommendation Agent ----
        recommendationAgent.recommend(applicationId);
    }

    // -----------------------------------------------------------------------
    // Task 8.1: Payload deserialisation
    // -----------------------------------------------------------------------

    private ExtractionData deserialisePayload(Application application) {
        UUID appId = application.getId();

        DocumentExtractionPayload payloadEntity = extractionPayloadRepository
                .findByApplication_Id(appId)
                .orElseThrow(() -> {
                    haltWithScoringError(application,
                            "No extraction payload found", "PAYLOAD_DESERIALISATION_FAILURE");
                    return new ScoringException(
                            "No extraction payload found for application " + appId,
                            "PAYLOAD_DESERIALISATION_FAILURE");
                });

        Map<String, Object> fields = payloadEntity.getExtractedFields();
        if (fields == null || fields.isEmpty()) {
            haltWithScoringError(application,
                    "Extraction payload is empty", "PAYLOAD_DESERIALISATION_FAILURE");
            throw new ScoringException(
                    "Extraction payload fields are empty for application " + appId,
                    "PAYLOAD_DESERIALISATION_FAILURE");
        }

        try {
            // Re-serialise and deserialise to validate round-trip integrity (Requirement 12.3)
            String json = objectMapper.writeValueAsString(fields);
            @SuppressWarnings("unchecked")
            Map<String, Object> restored = objectMapper.readValue(json, Map.class);

            return buildExtractionData(application, restored);
        } catch (JsonProcessingException e) {
            haltWithScoringError(application,
                    "Payload deserialisation failed: " + e.getMessage(),
                    "PAYLOAD_DESERIALISATION_FAILURE");
            // recordDeserFailureAudit is called inside haltWithScoringError
            throw new ScoringException(
                    "Payload deserialisation failed for application " + appId,
                    "PAYLOAD_DESERIALISATION_FAILURE");
        }
    }

    private ExtractionData buildExtractionData(Application application,
                                                Map<String, Object> fields) {
        ExtractionData data = new ExtractionData();

        // grossMonthlyIncome: prefer extracted value; fall back to application entity
        Object incomeObj = fields.get("grossMonthlyIncome");
        data.grossMonthlyIncome = incomeObj != null
                ? new BigDecimal(incomeObj.toString())
                : application.getGrossMonthlyIncome();

        data.totalMonthlyDebt = application.getTotalMonthlyDebt();

        Object monthsObj = fields.get("consecutiveIncomeMonths");
        data.consecutiveIncomeMonths = monthsObj != null
                ? ((Number) monthsObj).intValue()
                : 0;

        Object ratioObj = fields.get("onTimeRepaymentRatio");
        data.onTimeRepaymentRatio = ratioObj != null
                ? new BigDecimal(ratioObj.toString())
                : BigDecimal.ZERO;

        return data;
    }

    private void haltWithScoringError(Application application, String message, String code) {
        application.setStatus(ApplicationStatus.SCORING_ERROR);
        applicationRepository.save(application);

        Map<String, Object> payload = new HashMap<>();
        payload.put("errorCode", code);
        payload.put("message", message);

        if ("PAYLOAD_DESERIALISATION_FAILURE".equals(code)) {
            auditAgent.recordEvent(
                    AuditEventType.DESERIALISATION_FAILURE,
                    application.getId(),
                    "CreditPolicyAgent",
                    payload
            );
        } else {
            auditAgent.recordEvent(
                    AuditEventType.CREDIT_SCORE_COMPUTED,
                    application.getId(),
                    "CreditPolicyAgent",
                    payload
            );
        }
    }

    // -----------------------------------------------------------------------
    // Task 8.1: Zero-income guard
    // -----------------------------------------------------------------------

    private void guardZeroIncome(Application application, BigDecimal grossMonthlyIncome) {
        if (grossMonthlyIncome == null || grossMonthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            haltWithScoringError(application, "Gross monthly income is zero", "ZERO_INCOME");
            throw new ScoringException("Gross monthly income is zero", "ZERO_INCOME");
        }
    }

    // -----------------------------------------------------------------------
    // Task 8.3: Sub-score band mapping (Requirements 4.3, 4.4, 4.5)
    // Package-private for direct testing via property tests.
    // -----------------------------------------------------------------------

    /**
     * Maps the DTI ratio to a sub-score.
     *
     * <pre>
     * DTI ≤ 0.20 → 100
     * DTI  0.21–0.35 → 80
     * DTI  0.36–0.43 → 60
     * DTI  0.44–0.50 → 40
     * DTI  > 0.50 → 0
     * </pre>
     *
     * @param dti the debt-to-income ratio (non-negative)
     * @return sub-score in {0, 40, 60, 80, 100}
     */
    int computeDtiSubScore(BigDecimal dti) {
        // Use compareTo for exact decimal comparison
        if (dti.compareTo(new BigDecimal("0.20")) <= 0) return 100;
        if (dti.compareTo(new BigDecimal("0.35")) <= 0) return 80;
        if (dti.compareTo(new BigDecimal("0.43")) <= 0) return 60;
        if (dti.compareTo(new BigDecimal("0.50")) <= 0) return 40;
        return 0;
    }

    /**
     * Maps consecutive months of confirmed income to a sub-score.
     *
     * <pre>
     * months ≥ 24 → 100
     * months 12–23 → 70
     * months 6–11 → 40
     * months < 6 → 0
     * </pre>
     *
     * @param months number of consecutive income months (non-negative)
     * @return sub-score in {0, 40, 70, 100}
     */
    int computeIncomeStabilityScore(int months) {
        if (months >= 24) return 100;
        if (months >= 12) return 70;
        if (months >= 6)  return 40;
        return 0;
    }

    /**
     * Maps the on-time repayment ratio to a sub-score.
     *
     * <pre>
     * ratio ≥ 0.95 → 100
     * ratio 0.80–0.94 → 75
     * ratio 0.65–0.79 → 50
     * ratio < 0.65 → 20
     * </pre>
     *
     * @param repaymentRatio ratio in [0, 1] (may have more than 2 dp)
     * @return sub-score in {20, 50, 75, 100}
     */
    int computeCreditHistoryScore(BigDecimal repaymentRatio) {
        if (repaymentRatio.compareTo(new BigDecimal("0.95")) >= 0) return 100;
        if (repaymentRatio.compareTo(new BigDecimal("0.80")) >= 0) return 75;
        if (repaymentRatio.compareTo(new BigDecimal("0.65")) >= 0) return 50;
        return 20;
    }

    // -----------------------------------------------------------------------
    // Task 8.5: Weighted credit score formula (Requirement 4.6)
    // Package-private for direct testing via property tests.
    // -----------------------------------------------------------------------

    /**
     * Computes the final Credit Score using the weighted formula:
     *
     * <pre>
     * creditScore = (dtiSubScore × 0.40 + incomeStabilityScore × 0.35
     *                + creditHistoryScore × 0.25) × 10
     * </pre>
     *
     * Result is clamped to [0, 1000] and rounded to 2 decimal places.
     *
     * @param dtiSubScore          sub-score in [0, 100]
     * @param incomeStabilityScore sub-score in [0, 100]
     * @param creditHistoryScore   sub-score in [0, 100]
     * @return credit score in [0, 1000]
     */
    BigDecimal computeCreditScore(int dtiSubScore,
                                   int incomeStabilityScore,
                                   int creditHistoryScore) {
        BigDecimal raw = BigDecimal.valueOf(dtiSubScore)
                .multiply(DTI_WEIGHT)
                .add(BigDecimal.valueOf(incomeStabilityScore).multiply(INCOME_STABILITY_WEIGHT))
                .add(BigDecimal.valueOf(creditHistoryScore).multiply(CREDIT_HISTORY_WEIGHT))
                .multiply(BigDecimal.TEN)
                .setScale(2, RoundingMode.HALF_UP);

        // Clamp to [0, 1000]
        if (raw.compareTo(BigDecimal.ZERO) < 0)           return BigDecimal.ZERO.setScale(2);
        if (raw.compareTo(new BigDecimal("1000")) > 0)    return new BigDecimal("1000.00");
        return raw;
    }

    // -----------------------------------------------------------------------
    // Internal value object for extracted fields
    // -----------------------------------------------------------------------

    private static class ExtractionData {
        BigDecimal grossMonthlyIncome;
        BigDecimal totalMonthlyDebt;
        int        consecutiveIncomeMonths;
        BigDecimal onTimeRepaymentRatio;
    }
}
