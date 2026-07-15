package com.techvestai.project.controller;

import com.techvestai.project.dto.request.ApplicationSubmitRequest;
import com.techvestai.project.dto.response.ApplicationReviewResponse;
import com.techvestai.project.dto.response.ApplicationStatusResponse;
import com.techvestai.project.dto.response.ApplicationSummaryResponse;
import com.techvestai.project.entity.User;
import com.techvestai.project.security.UserPrincipal;
import com.techvestai.project.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Application lifecycle controller — Task 13.2.
 *
 * <p>Exposes submission, status polling, paginated worklist, and detail-review
 * endpoints per the RBAC matrix in the design document.
 *
 * <p><b>Requirements:</b> 2.1, 2.2, 6.8, 7.1, 7.2, 10.1–10.5
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // -----------------------------------------------------------------------
    // APPLICANT — submit application
    // -----------------------------------------------------------------------

    /**
     * POST /api/v1/applications
     * Creates a new application and triggers the agent pipeline.
     * Returns 201 with a Location header and the generated applicationId.
     */
    @PostMapping
    public ResponseEntity<Void> submit(
            @Valid @RequestBody ApplicationSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User applicant = principal.getUser();
        UUID applicationId = applicationService.submitApplication(request, applicant);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}/status")
                .buildAndExpand(applicationId)
                .toUri();

        return ResponseEntity.created(location).build();
    }

    // -----------------------------------------------------------------------
    // APPLICANT — own application status
    // -----------------------------------------------------------------------

    /**
     * GET /api/v1/applications/{id}/status
     * Returns current status for the authenticated applicant.
     * Ownership is enforced in the service layer (→ 404 on violation).
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<ApplicationStatusResponse> getStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
                applicationService.getStatusForApplicant(id, principal.getUser()));
    }

    // -----------------------------------------------------------------------
    // UNDERWRITER / ADMIN — paginated worklist
    // -----------------------------------------------------------------------

    /**
     * GET /api/v1/applications
     * Returns a paginated list of applications in AWAITING_UNDERWRITER_REVIEW.
     */
    @GetMapping
    public ResponseEntity<Page<ApplicationSummaryResponse>> list(
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable) {

        return ResponseEntity.ok(
                applicationService.listApplicationsForUnderwriter(pageable));
    }

    // -----------------------------------------------------------------------
    // UNDERWRITER / ADMIN — full review detail
    // -----------------------------------------------------------------------

    /**
     * GET /api/v1/applications/{id}/review
     * Returns the full review payload including credit score, recommendation,
     * and fairness evaluation. Surfaces FAIRNESS_FLAG when present (Req. 6.8).
     */
    @GetMapping("/{id}/review")
    public ResponseEntity<ApplicationReviewResponse> review(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getReviewForUnderwriter(id));
    }
}
