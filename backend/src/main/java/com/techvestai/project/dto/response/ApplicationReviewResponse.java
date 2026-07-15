package com.techvestai.project.dto.response;

import com.techvestai.project.enums.ApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationReviewResponse(
        UUID applicationId,
        ApplicationStatus status,
        ApplicationFormData formData,
        List<DocumentMetadata> documents,
        CreditScoreBreakdown creditScore,
        RecommendationDetail recommendation,
        FairnessResultResponse fairnessResult,
        boolean hasFairnessFlag,
        String fairnessFlagReason,
        Instant createdAt
) {}
