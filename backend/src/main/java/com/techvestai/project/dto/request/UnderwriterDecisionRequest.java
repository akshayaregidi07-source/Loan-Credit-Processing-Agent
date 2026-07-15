package com.techvestai.project.dto.request;

import com.techvestai.project.enums.DecisionValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UnderwriterDecisionRequest(
        @NotNull DecisionValue decisionValue,
        @NotBlank @Size(min = 20, max = 5000) String justificationText,
        @Size(max = 2000) String overrideReason
) {}
