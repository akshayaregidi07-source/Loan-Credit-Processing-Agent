package com.techvestai.project.agent;

import com.techvestai.project.entity.Application;
import com.techvestai.project.entity.CreditScore;
import com.techvestai.project.entity.DocumentExtractionPayload;
import com.techvestai.project.entity.FairnessResult;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.FairnessOutcome;
import com.techvestai.project.exception.ScoringException;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.CreditScoreRepository;
import com.techvestai.project.repository.DocumentExtractionPayloadRepository;
import com.techvestai.project.repository.FairnessResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fairness Agent — Tasks 10.1 and 10.2.
 *
 * <p>Re-scores each application after stripping all Protected Attributes
 * (name, national ID, gender, date of birth, ethnicity, address) from the
 * input. The delta between the original and anonymised scores is used to
 * detect potential demographic bias before the application reaches an
 * Underwriter.
 *
 * <h3>Pipeline step</h3>
 * <ol>
 *   <li><b>Task 10.1 — Anonymised re-scoring</b>
 *       <br>Load the persisted {@link DocumentExtractionPayload} and build a
 *       transient, in-memory {@code FinancialInputs} object that contains
 *       <em>only</em> the financial fields (grossMonthlyIncome, totalMonthlyDebt,
 *       consecutiveIncomeMonths, onTimeRepaymentRatio). Protected Attributes
 *       are deliberately excluded and are never stored anywhere during this
 *       step (Requirement 13.5).
 *       <br>Call the same {@link CreditPolicyAgent} computation methods on the
 *       anonymised inputs to produce {@code anonymisedCreditScore}.
 *   <li><b>Task 10.2 — Delta computation and flag logic</b>
 *       <br>Compute {@code fairnessDelta = |originalCreditScore − anonymisedCreditScore|}.
 *       <br>If delta ≥ 50 → {@code FAIRNESS_FLAG} with reason
 *       {@code "POTENTIAL_BIAS_DETECTED"}.
 *       <br>If delta &lt; 50 → {@code FAIRNESS_PASSED}.
 *       <br>Persist the {@link FairnessResult} entity.
 *       <br>The original {@link CreditScore} and {@link com.techvestai.project.entity.Recommendation}
 *       records are <em>not</em> modified (Requirement 6.6).
 *       <br>Record a {@code FAIRNESS_EVALUATION_COMPLETED} audit event.
 *       <br>Set Application status to {@code AWAITING_UNDERWRITER_REVIEW}.
 * </ol>
 *
 * <p><b>Requirements:</b> 6.1–6.7, 13.5
 */
@Component
public class FairnessAgent {

    private static final Logger log = LoggerFactory.getLogger(FairnessAgent.class);

    /** Delta threshold above which a FAIRNESS_FLAG is raised (inclusive). */
    static final BigDecimal FLAG_THRESHOLD = new BigDecimal("50");

    private final ApplicationRepository              applicationRepository;
    private final CreditScoreRepository              creditScoreRepository;
    private final DocumentExtractionPayloadRepository extractionPayloadRepository;
    private final FairnessResultRepository           fairnessResultRepository;
    private final AuditAgent                         auditAgent;
    private final CreditPolicyAgent                  creditPolicyAgent;

    public FairnessAgent(ApplicationRepository applicationRepository,
                         CreditScoreRepository creditScoreRepository,
                         DocumentExtractionPayloadRepository extractionPayloadRepository,
                         FairnessResultRepository fairnessResultRepository,
                         AuditAgent auditAgent,
                         CreditPolicyAgent creditPolicyAgent) {
        this.applicationRepository     = applicationRepository;
        this.creditScoreRepository     = creditScoreRepository;
        this.extractionPayloadRepository = extractionPayloadRepository;
        this.fairnessResultRepository  = fairnessResultRepository;
        this.auditAgent                = auditAgent;
        this.creditPolicyAgent         = creditPolicyAgent;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Evaluates the application for potential demographic bias.
     * Called by {@link RecommendationAgent} after a recommendation is produced.
     *
     * @param applicationId the application to evaluate
     */
    @Transactional
    public void evaluate(UUID applicationId) {
        log.info("FairnessAgent starting for application {}", applicationId);

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Application not found: " + applicationId));

        // ---- Task 10.1: load original credit score (must not be modified) ----
        CreditScore originalCreditScore = creditScoreRepository
                .findByApplication_Id(applicationId)
                .orElseThrow(() -> new ScoringException(
                        "Credit score not found for application " + applicationId,
                        "CREDIT_SCORE_MISSING"));

        // ---- Task 10.1: build anonymised financial inputs ----
        // Protected Attributes are stripped here; the FinancialInputs object
        // is transient (never persisted). Only financial fields are included.
        FinancialInputs anonymised = buildAnonymisedInputs(application, applicationId);

        // ---- Task 10.1: re-score on anonymised inputs ----
        BigDecimal anonymisedScore = reScore(anonymised);

        // ---- Task 10.2: compute absolute delta ----
        BigDecimal delta = computeDelta(originalCreditScore.getCreditScore(), anonymisedScore);

        // ---- Task 10.2: flag logic ----
        FairnessOutcome outcome;
        String flagReason;
        if (delta.compareTo(FLAG_THRESHOLD) >= 0) {
            outcome    = FairnessOutcome.FAIRNESS_FLAG;
            flagReason = "POTENTIAL_BIAS_DETECTED";
        } else {
            outcome    = FairnessOutcome.FAIRNESS_PASSED;
            flagReason = null;
        }

        // ---- Task 10.2: persist FairnessResult ----
        // The original CreditScore and Recommendation are NOT touched here.
        FairnessResult result = new FairnessResult();
        result.setApplication(application);
        result.setOriginalCreditScore(originalCreditScore.getCreditScore());
        result.setAnonymisedCreditScore(anonymisedScore);
        result.setFairnessDelta(delta);
        result.setFairnessOutcome(outcome);
        result.setFlagReason(flagReason);
        fairnessResultRepository.save(result);

        // ---- Task 10.2: set application status to AWAITING_UNDERWRITER_REVIEW ----
        application.setStatus(ApplicationStatus.AWAITING_UNDERWRITER_REVIEW);
        applicationRepository.save(application);

        // ---- Task 10.2: audit event ----
        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("originalCreditScore",    originalCreditScore.getCreditScore().toPlainString());
        auditPayload.put("anonymisedCreditScore",  anonymisedScore.toPlainString());
        auditPayload.put("fairnessDelta",          delta.toPlainString());
        auditPayload.put("fairnessOutcome",        outcome.name());
        if (flagReason != null) {
            auditPayload.put("flagReason", flagReason);
        }

        auditAgent.recordEvent(
                AuditEventType.FAIRNESS_EVALUATION_COMPLETED,
                applicationId,
                "FairnessAgent",
                auditPayload
        );

        log.info("FairnessAgent completed for application {} — outcome={}, delta={}",
                applicationId, outcome, delta);
    }

    // -----------------------------------------------------------------------
    // Task 10.1: Build anonymised financial inputs (Requirement 13.5)
    // -----------------------------------------------------------------------

    /**
     * Loads only the financial fields from the extraction payload and the
     * Application entity, deliberately omitting all Protected Attributes.
     *
     * <p>Protected Attributes excluded (never included in the returned object):
     * applicantName, dateOfBirth, nationalId, gender, ethnicity, address.
     *
     * <p>The returned {@link FinancialInputs} is a plain value object; it is
     * never saved to the database.
     */
    private FinancialInputs buildAnonymisedInputs(Application application, UUID applicationId) {
        // Prefer values from the extraction payload (parsed from documents)
        DocumentExtractionPayload payload = extractionPayloadRepository
                .findByApplication_Id(applicationId)
                .orElse(null);

        FinancialInputs inputs = new FinancialInputs();

        if (payload != null && payload.getExtractedFields() != null) {
            Map<String, Object> fields = payload.getExtractedFields();

            Object incomeObj = fields.get("grossMonthlyIncome");
            inputs.grossMonthlyIncome = (incomeObj != null)
                    ? new BigDecimal(incomeObj.toString())
                    : application.getGrossMonthlyIncome();

            Object monthsObj = fields.get("consecutiveIncomeMonths");
            inputs.consecutiveIncomeMonths = (monthsObj != null)
                    ? ((Number) monthsObj).intValue()
                    : 0;

            Object ratioObj = fields.get("onTimeRepaymentRatio");
            inputs.onTimeRepaymentRatio = (ratioObj != null)
                    ? new BigDecimal(ratioObj.toString())
                    : BigDecimal.ZERO;
        } else {
            // Fallback: use application-level financial values only
            inputs.grossMonthlyIncome      = application.getGrossMonthlyIncome();
            inputs.consecutiveIncomeMonths = 0;
            inputs.onTimeRepaymentRatio    = BigDecimal.ZERO;
        }

        // totalMonthlyDebt always comes from the application form (no document source)
        inputs.totalMonthlyDebt = application.getTotalMonthlyDebt();

        // Protected Attributes are deliberately NOT included —
        // applicantName, dateOfBirth, nationalId, gender, ethnicity, address
        // are never set on this object.

        return inputs;
    }

    // -----------------------------------------------------------------------
    // Task 10.1: Re-execute CreditPolicyAgent scoring on anonymised inputs
    // -----------------------------------------------------------------------

    /**
     * Computes the Credit Score for a set of anonymised financial inputs by
     * reusing the same pure computation methods from {@link CreditPolicyAgent}.
     *
     * <p>No database writes occur inside this method.
     *
     * @param inputs anonymised financial data (no protected attributes)
     * @return credit score in [0, 1000]
     */
    private BigDecimal reScore(FinancialInputs inputs) {
        // DTI ratio
        BigDecimal dtiRatio;
        if (inputs.grossMonthlyIncome == null
                || inputs.grossMonthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            dtiRatio = BigDecimal.ZERO;
        } else {
            dtiRatio = inputs.totalMonthlyDebt.divide(
                    inputs.grossMonthlyIncome, 2, RoundingMode.HALF_UP);
        }

        // Sub-scores using the same band tables as CreditPolicyAgent
        int dtiSubScore          = creditPolicyAgent.computeDtiSubScore(dtiRatio);
        int incomeStabilityScore = creditPolicyAgent.computeIncomeStabilityScore(
                inputs.consecutiveIncomeMonths);
        int creditHistoryScore   = creditPolicyAgent.computeCreditHistoryScore(
                inputs.onTimeRepaymentRatio);

        // Weighted formula — identical to CreditPolicyAgent.computeCreditScore()
        return creditPolicyAgent.computeCreditScore(
                dtiSubScore, incomeStabilityScore, creditHistoryScore);
    }

    // -----------------------------------------------------------------------
    // Task 10.2: Delta computation (Requirement 6.3)
    // Package-private for direct use by property tests.
    // -----------------------------------------------------------------------

    /**
     * Computes the absolute difference between the original and anonymised
     * credit scores.
     *
     * <p>Property: result is always ≥ 0 (Requirement 6.3).
     *
     * @param original    the original credit score
     * @param anonymised  the re-scored credit score on anonymised inputs
     * @return |original − anonymised|, rounded to 2 decimal places
     */
    BigDecimal computeDelta(BigDecimal original, BigDecimal anonymised) {
        return original.subtract(anonymised).abs().setScale(2, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // Task 10.2: Flag threshold logic (Requirements 6.4, 6.5)
    // Package-private for direct use by property tests.
    // -----------------------------------------------------------------------

    /**
     * Determines the fairness outcome for a given delta value.
     *
     * <ul>
     *   <li>delta ≥ 50 → {@code FAIRNESS_FLAG}
     *   <li>delta &lt; 50 → {@code FAIRNESS_PASSED}
     * </ul>
     *
     * <p>The boundary value 50 triggers {@code FAIRNESS_FLAG} (Requirement 6.4).
     *
     * @param delta the absolute score delta (non-negative)
     * @return the appropriate {@link FairnessOutcome}
     */
    FairnessOutcome determineFairnessOutcome(BigDecimal delta) {
        return delta.compareTo(FLAG_THRESHOLD) >= 0
                ? FairnessOutcome.FAIRNESS_FLAG
                : FairnessOutcome.FAIRNESS_PASSED;
    }

    // -----------------------------------------------------------------------
    // Internal value object — anonymised financial inputs only
    // -----------------------------------------------------------------------

    /**
     * Transient holder for the financial inputs used during anonymised
     * re-scoring. Never persisted; never contains Protected Attributes.
     */
    static final class FinancialInputs {
        BigDecimal grossMonthlyIncome;
        BigDecimal totalMonthlyDebt;
        int        consecutiveIncomeMonths;
        BigDecimal onTimeRepaymentRatio;
    }
}
