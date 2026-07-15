package com.techvestai.project.dto.response;

import com.techvestai.project.enums.ApplicationStatus;
import com.techvestai.project.enums.DecisionValue;

import java.time.Instant;
import java.util.UUID;

public record ApplicationStatusResponse(
        UUID applicationId,
        ApplicationStatus status,
        Instant lastUpdatedAt,
        DecisionValue decisionValue,   // null unless DECISION_RECORDED
        Instant decisionTimestamp      // null unless DECISION_RECORDED
) {}
