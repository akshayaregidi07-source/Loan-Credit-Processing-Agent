package com.techvestai.project.dto.response;

import com.techvestai.project.enums.RecommendationValue;

import java.time.Instant;
import java.util.UUID;

public record RecommendationDetail(
        RecommendationValue recommendationValue,
        UUID policyThresholdId,
        String explanation,
        Instant producedAt
) {}
