package com.techvestai.project.controller;

import com.techvestai.project.dto.request.PolicyThresholdRequest;
import com.techvestai.project.entity.PolicyThreshold;
import com.techvestai.project.security.UserPrincipal;
import com.techvestai.project.service.PolicyThresholdService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Policy threshold controller — Task 13.9.
 *
 * <p>GET  /api/v1/policies — UNDERWRITER + ADMIN: list all thresholds.<br>
 * POST /api/v1/policies — ADMIN only: create a new active threshold.
 *
 * <p><b>Requirements:</b> 9.1–9.3, 9.6
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyThresholdService policyThresholdService;

    public PolicyController(PolicyThresholdService policyThresholdService) {
        this.policyThresholdService = policyThresholdService;
    }

    /**
     * GET /api/v1/policies
     * Returns all threshold records ordered by creation timestamp descending.
     * The ACTIVE record appears first.
     */
    @GetMapping
    public ResponseEntity<List<PolicyThreshold>> listAll() {
        return ResponseEntity.ok(policyThresholdService.listAllThresholds());
    }

    /**
     * POST /api/v1/policies
     * Creates a new ACTIVE policy threshold and supersedes the previous one.
     * Returns 422 if approveThreshold ≤ referThreshold.
     */
    @PostMapping
    public ResponseEntity<PolicyThreshold> create(
            @Valid @RequestBody PolicyThresholdRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        PolicyThreshold created =
                policyThresholdService.createThreshold(request, principal.getUser());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
