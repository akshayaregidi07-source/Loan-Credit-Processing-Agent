package com.techvestai.project.agent;

import com.techvestai.project.entity.Application;
import com.techvestai.project.entity.CreditScore;
import com.techvestai.project.entity.PolicyThreshold;
import com.techvestai.project.entity.Recommendation;
import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.enums.PolicyThresholdStatus;
import com.techvestai.project.enums.RecommendationValue;
import com.techvestai.project.exception.ScoringException;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.CreditScoreRepository;
import com.techvestai.project.repository.PolicyThresholdRepository;
import com.techvestai.project.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recommendation Agent — Task 9.1.
 *
 * <p>Evaluates the computed Credit Score against the active Policy Threshold
 * and produces one of three recommendations: APPROVE, REFER, or DECLINE.
 *
 * <h3>Pipeline step</h3>
 * <ol>
 *   <li>Load the persisted {@link CreditScore} for the application.
 *   <li>Load the active {@link PolicyThreshold}.
 *   <li>Evaluate score against thresholds:
 *       <ul>
 *         <li>score ≥ approveThreshold → APPROVE
 *         <li>score ≥ referThreshold   → REFER
 *         <li>score &lt; referThreshold  → DECLINE
 *       </ul>
 *   <li>Build a human-readable explanation listing each scoring factor with its
 *       value, weight, and contribution to the final score.
 *   <li>Persist the {@link Recommendation} entity with a reference to the
 *       policy threshold version used (traceability).
 *   <li>Record a {@code RECOMMENDATION_PRODUCED} audit event.
 *   <li>Trigger {@link FairnessAgent#evaluate(UUID)}.
 * </ol>
 *
 * <p>The {@link FairnessAgent} dependency is injected with {@code @Lazy} to
 * break the DVA → CPA → RA → FA bean-creation cycle.
 *
 * <p><b>Requirements:</b> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
@Component
public class RecommendationAgent {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAgent.class);

    private final ApplicationRepository     applicationRepository;
    private final CreditScoreRepository     creditScoreRepository;
    private final PolicyThresholdRepository policyThresholdRepository;
    private final RecommendationRepository  recommendationRepository;
    private final AuditAgent                auditAgent;
    private final FairnessAgent             fairnessAgent;

    public RecommendationAgent(ApplicationRepository applicationRepository,
                               CreditScoreRepository creditScoreRepository,
                               PolicyThresholdRepository policyThresholdRepository,
                               RecommendationRepository recommendationRepository,
                               AuditAgent auditAgent,
                               @Lazy FairnessAgent fairnessAgent) {
        this.applicationRepository     = applicationRepository;
        this.creditScoreRepository     = creditScoreRepository;
        this.policyThresholdRepository = policyThresholdRepository;
        this.recommendationRepository  = recommendationRepository;
        this.auditAgent                = auditAgent;
        this.fairnessAgent             = fairnessAgent;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Produces a recommendation for the given application.
     * Called by {@link CreditPolicyAgent} immediately after the Credit Score
     * is persisted.
     *
     * @param applicationId the application to recommend on
     * @throws ScoringException if the credit score record cannot be found
     */
    @Transactional
    public void recommend(UUID applicationId) {
        log.info("RecommendationAgent starting for application {}", applicationId);

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Application not found: " + applicationId));

        // ---- Load credit score ----
        CreditScore creditScore = creditScoreRepository.findByApplication_Id(applicationId)
                .orElseThrow(() -> new ScoringException(
                        "Credit score not found for application " + applicationId,
                        "CREDIT_SCORE_MISSING"));

        // ---- Load active policy threshold ----
        PolicyThreshold activePolicy = policyThresholdRepository
                .findByStatus(PolicyThresholdStatus.ACTIVE)
                .orElseThrow(() -> new ScoringException(
                        "No active policy threshold found", "NO_ACTIVE_POLICY"));

        // ---- Requirement 5.1–5.4: score-to-recommendation mapping ----
        RecommendationValue recommendationValue =
                mapScoreToRecommendation(creditScore.getCreditScore(), activePolicy);

        // ---- Requirement 5.6: build explanation ----
        String explanation = buildExplanation(creditScore, activePolicy);

        // ---- Requirement 5.5: persist Recommendation with policy citation ----
        Recommendation recommendation = new Recommendation();
        recommendation.setApplication(application);
        recommendation.setRecommendationValue(recommendationValue);
        recommendation.setPolicyThreshold(activePolicy);
        recommendation.setExplanation(explanation);
        recommendationRepository.save(recommendation);

        // ---- Requirement 5.7: audit event ----
        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("recommendationValue",  recommendationValue.name());
        auditPayload.put("creditScore",          creditScore.getCreditScore().toPlainString());
        auditPayload.put("policyThresholdId",    activePolicy.getId().toString());
        auditPayload.put("approveThreshold",     activePolicy.getApproveThreshold());
        auditPayload.put("referThreshold",       activePolicy.getReferThreshold());

        auditAgent.recordEvent(
                AuditEventType.RECOMMENDATION_PRODUCED,
                applicationId,
                "RecommendationAgent",
                auditPayload
        );

        log.info("RecommendationAgent produced {} for application {} (score={})",
                recommendationValue, applicationId, creditScore.getCreditScore());

        // ---- Trigger Fairness Agent ----
        fairnessAgent.evaluate(applicationId);
    }

    // -----------------------------------------------------------------------
    // Score-to-recommendation mapping (Requirements 5.2, 5.3, 5.4)
    // Package-private so property tests can call it directly.
    // -----------------------------------------------------------------------

    /**
     * Maps a credit score to a recommendation value using the supplied
     * policy thresholds.
     *
     * <p>Invariant: exactly one of APPROVE / REFER / DECLINE is returned;
     * no other value is ever produced (Requirement 5.1).
     *
     * @param score        the computed credit score (0–1000)
     * @param activePolicy the active policy threshold record
     * @return APPROVE, REFER, or DECLINE
     */
    RecommendationValue mapScoreToRecommendation(BigDecimal score,
                                                  PolicyThreshold activePolicy) {
        BigDecimal approveThreshold = BigDecimal.valueOf(activePolicy.getApproveThreshold());
        BigDecimal referThreshold   = BigDecimal.valueOf(activePolicy.getReferThreshold());

        if (score.compareTo(approveThreshold) >= 0) {
            return RecommendationValue.APPROVE;
        }
        if (score.compareTo(referThreshold) >= 0) {
            return RecommendationValue.REFER;
        }
        return RecommendationValue.DECLINE;
    }

    // -----------------------------------------------------------------------
    // Explanation builder (Requirement 5.6)
    // -----------------------------------------------------------------------

    /**
     * Builds a human-readable explanation that lists each scoring factor by
     * name, sub-score value, weight, and its contribution to the final Credit
     * Score. Also cites the policy threshold version used.
     *
     * <p>Example output (abbreviated):
     * <pre>
     * Credit Score: 820.00 | Recommendation: APPROVE
     * Policy threshold version: &lt;uuid&gt; (approve ≥ 700, refer ≥ 500)
     *
     * Factor breakdown:
     *   DTI Ratio:         sub-score=100, weight=0.40, contribution=400.00
     *   Income Stability:  sub-score=100, weight=0.35, contribution=350.00
     *   Credit History:    sub-score= 75, weight=0.25, contribution=187.50
     * </pre>
     *
     * @param creditScore  the persisted credit score entity
     * @param activePolicy the active policy threshold used for the recommendation
     * @return multi-line plain-text explanation string
     */
    String buildExplanation(CreditScore creditScore, PolicyThreshold activePolicy) {
        BigDecimal score = creditScore.getCreditScore();

        BigDecimal dtiContribution =
                BigDecimal.valueOf(creditScore.getDtiSubScore())
                          .multiply(creditScore.getDtiWeight())
                          .multiply(BigDecimal.TEN);

        BigDecimal incomeContribution =
                BigDecimal.valueOf(creditScore.getIncomeStabilityScore())
                          .multiply(creditScore.getIncomeStabilityWeight())
                          .multiply(BigDecimal.TEN);

        BigDecimal creditHistoryContribution =
                BigDecimal.valueOf(creditScore.getCreditHistoryScore())
                          .multiply(creditScore.getCreditHistoryWeight())
                          .multiply(BigDecimal.TEN);

        RecommendationValue rec = mapScoreToRecommendation(score, activePolicy);

        return String.format(
                "Credit Score: %s | Recommendation: %s%n" +
                "Policy threshold version: %s (approve >= %d, refer >= %d)%n" +
                "%n" +
                "Factor breakdown:%n" +
                "  DTI Ratio:        sub-score=%d, weight=%s, contribution=%s%n" +
                "  Income Stability: sub-score=%d, weight=%s, contribution=%s%n" +
                "  Credit History:   sub-score=%d, weight=%s, contribution=%s",
                score.toPlainString(),
                rec.name(),
                activePolicy.getId().toString(),
                activePolicy.getApproveThreshold(),
                activePolicy.getReferThreshold(),
                creditScore.getDtiSubScore(),
                creditScore.getDtiWeight().toPlainString(),
                dtiContribution.toPlainString(),
                creditScore.getIncomeStabilityScore(),
                creditScore.getIncomeStabilityWeight().toPlainString(),
                incomeContribution.toPlainString(),
                creditScore.getCreditHistoryScore(),
                creditScore.getCreditHistoryWeight().toPlainString(),
                creditHistoryContribution.toPlainString()
        );
    }
}
