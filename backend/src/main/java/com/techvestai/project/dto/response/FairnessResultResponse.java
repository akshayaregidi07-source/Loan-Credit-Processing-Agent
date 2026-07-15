package com.techvestai.project.dto.response;

import com.techvestai.project.enums.FairnessOutcome;

import java.math.BigDecimal;
import java.time.Instant;

public record FairnessResultResponse(
        BigDecimal originalCreditScore,
        BigDecimal anonymisedCreditScore,
        BigDecimal fairnessDelta,
        FairnessOutcome fairnessOutcome,
        String flagReason,
        Instant evaluatedAt
) {}
