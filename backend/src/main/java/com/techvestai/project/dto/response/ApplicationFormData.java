package com.techvestai.project.dto.response;

import com.techvestai.project.enums.EmploymentStatus;

import java.math.BigDecimal;

public record ApplicationFormData(
        BigDecimal requestedAmount,
        String loanPurpose,
        EmploymentStatus employmentStatus,
        BigDecimal grossMonthlyIncome,
        BigDecimal totalMonthlyDebt
) {}
