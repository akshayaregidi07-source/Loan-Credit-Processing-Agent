package com.techvestai.project.service;

import com.techvestai.project.dto.request.PolicyThresholdRequest;
import com.techvestai.project.entity.PolicyThreshold;
import com.techvestai.project.entity.User;
import com.techvestai.project.enums.PolicyThresholdStatus;
import com.techvestai.project.exception.ScoringException;
import com.techvestai.project.repository.PolicyThresholdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Policy threshold management service — Task 12.3.
 *
 * <p>Manages the versioned credit score threshold records. At all times
 * exactly one record has status ACTIVE; all others are SUPERSEDED
 * (Requirement 9.3 — invariant enforced atomically in {@link #createThreshold}).
 *
 * <p><b>Requirements:</b> 9.1–9.6
 */
@Service
@Transactional(readOnly = true)
public class PolicyThresholdService {

    private final PolicyThresholdRepository policyThresholdRepository;

    public PolicyThresholdService(PolicyThresholdRepository policyThresholdRepository) {
        this.policyThresholdRepository = policyThresholdRepository;
    }

    // -----------------------------------------------------------------------
    // Create (Requirements 9.1, 9.2, 9.3)
    // -----------------------------------------------------------------------

    /**
     * Creates a new ACTIVE policy threshold and supersedes the previous one.
     *
     * <p>Validation: {@code approveThreshold} must be strictly greater than
     * {@code referThreshold}; otherwise HTTP 422 is returned (Requirement 9.2).
     *
     * @param request the new threshold values
     * @param creator the authenticated admin user
     * @return the newly created {@link PolicyThreshold}
     */
    @Transactional
    public PolicyThreshold createThreshold(PolicyThresholdRequest request, User creator) {
        // Requirement 9.2 — approve must be greater than refer
        if (request.approveThreshold() <= request.referThreshold()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "approveThreshold must be greater than referThreshold");
        }

        // Supersede the current active record (Requirement 9.3)
        policyThresholdRepository.findByStatus(PolicyThresholdStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(PolicyThresholdStatus.SUPERSEDED);
                    policyThresholdRepository.save(current);
                });

        // Persist the new active record
        PolicyThreshold newThreshold = new PolicyThreshold();
        newThreshold.setApproveThreshold(request.approveThreshold());
        newThreshold.setReferThreshold(request.referThreshold());
        newThreshold.setStatus(PolicyThresholdStatus.ACTIVE);
        newThreshold.setCreatedBy(creator);

        return policyThresholdRepository.save(newThreshold);
    }

    // -----------------------------------------------------------------------
    // Read (Requirements 9.4, 9.6)
    // -----------------------------------------------------------------------

    /**
     * Returns the single ACTIVE policy threshold.
     *
     * @throws ScoringException if no active threshold exists
     */
    public PolicyThreshold getActiveThreshold() {
        return policyThresholdRepository.findByStatus(PolicyThresholdStatus.ACTIVE)
                .orElseThrow(() -> new ScoringException(
                        "No active policy threshold found", "NO_ACTIVE_POLICY"));
    }

    /**
     * Returns all policy threshold records ordered by creation timestamp
     * descending (Requirement 9.6). The ACTIVE record appears first.
     */
    public List<PolicyThreshold> listAllThresholds() {
        return policyThresholdRepository.findAllByOrderByCreatedAtDesc();
    }
}
