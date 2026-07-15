package com.techvestai.project.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditScoreBreakdown(
        BigDecimal creditScore,
        BigDecimal dtiRatio,
        int dtiSubScore,
        int incomeStabilityScore,
        int creditHistoryScore,
        BigDecimal dtiWeight,
        BigDecimal incomeStabilityWeight,
        BigDecimal creditHistoryWeight,
        UUID policyThresholdId,
        Instant computedAt
) {}
