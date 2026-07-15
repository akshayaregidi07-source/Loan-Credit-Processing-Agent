package com.techvestai.project.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PolicyThresholdRequest(
        @NotNull @Min(0) @Max(1000) Integer approveThreshold,
        @NotNull @Min(0) @Max(1000) Integer referThreshold
) {}
