package com.techvestai.project.dto.request;

import com.techvestai.project.enums.EmploymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ApplicationSubmitRequest(
        @NotNull @DecimalMin("1.00") BigDecimal requestedAmount,
        @NotBlank @Size(max = 200) String loanPurpose,
        @NotNull EmploymentStatus employmentStatus,
        @NotNull @DecimalMin("0.01") BigDecimal grossMonthlyIncome,
        @NotNull @DecimalMin("0.00") BigDecimal totalMonthlyDebt
) {}
