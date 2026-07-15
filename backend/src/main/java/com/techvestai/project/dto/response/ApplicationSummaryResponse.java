package com.techvestai.project.dto.response;

import com.techvestai.project.enums.ApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApplicationSummaryResponse(
        UUID applicationId,
        ApplicationStatus status,
        BigDecimal requestedAmount,
        String loanPurpose,
        Instant createdAt,
        Instant updatedAt
) {}
