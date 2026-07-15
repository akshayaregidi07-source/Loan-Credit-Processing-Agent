package com.techvestai.project.controller;

import com.techvestai.project.dto.request.UnderwriterDecisionRequest;
import com.techvestai.project.security.UserPrincipal;
import com.techvestai.project.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Underwriter decision controller — Task 13.6.
 *
 * <p>POST /api/v1/applications/{id}/decision records the underwriter's final
 * decision. Bean Validation enforces {@code justificationText ≥ 20} characters;
 * violations are mapped to HTTP 422 by {@link com.techvestai.project.exception.GlobalExceptionHandler}.
 * If the application is already in DECISION_RECORDED status the service layer
 * throws an {@link IllegalStateException} which the handler maps to 422.
 *
 * <p><b>Requirements:</b> 7.3–7.7
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/decision")
public class DecisionController {

    private final ApplicationService applicationService;

    public DecisionController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * POST /api/v1/applications/{applicationId}/decision
     *
     * @param applicationId the application being decided
     * @param request       validated decision payload (value + justification ≥ 20 chars)
     * @param principal     the authenticated underwriter
     * @return 200 on success; 422 on validation failure or already-decided application
     */
    @PostMapping
    public ResponseEntity<Void> recordDecision(
            @PathVariable UUID applicationId,
            @Valid @RequestBody UnderwriterDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        applicationService.recordDecision(applicationId, request, principal.getUser());
        return ResponseEntity.ok().build();
    }
}
