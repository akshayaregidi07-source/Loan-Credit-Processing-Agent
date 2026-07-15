package com.techvestai.project.agent;

import com.techvestai.project.entity.Document;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link DocumentParser}.
 *
 * <p>Returns deterministic values derived from document metadata so that the
 * full agent pipeline can be exercised without real OCR or PDF-parsing
 * libraries. Replace this bean with a production implementation when the
 * OCR/PDF capability is available.
 *
 * <p>Name extraction: returns the filename stem (everything before the first
 * dot) so that a Government-issued ID and an Income Proof uploaded with the
 * same filename stem will pass the cross-document consistency check.
 *
 * <p><b>Requirements:</b> 3.5, 3.6, 12.1
 */
@Component
public class StubDocumentParser implements DocumentParser {

    @Override
    public String extractApplicantName(Document document) {
        // Derive a consistent name from the original filename stem
        String filename = document.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return "UNKNOWN";
        }
        int dot = filename.lastIndexOf('.');
        String stem = (dot > 0) ? filename.substring(0, dot) : filename;
        // Strip common type prefixes so "id_john_doe.pdf" and "income_john_doe.pdf"
        // both yield "john_doe" — making the cross-doc check deterministic in tests.
        return stem.replaceFirst("^(id_|gov_|income_|bank_|statement_)", "")
                   .toLowerCase();
    }

    @Override
    public String extractDateOfBirth(Document document) {
        // Stub: return a fixed plausible date — real parsing replaces this
        return "1990-01-01";
    }

    @Override
    public String extractGrossMonthlyIncome(Document document) {
        // Stub: income is also present on the Application entity; the agent
        // uses the application's grossMonthlyIncome field for scoring and
        // stores it in the extraction payload for audit purposes.
        return null; // signal to caller: use application-level value
    }

    @Override
    public int extractConsecutiveIncomeMonths(Document document) {
        // Stub: assume 24 months of stable income → Income Stability Score = 100
        return 24;
    }

    @Override
    public double extractOnTimeRepaymentRatio(Document document) {
        // Stub: assume perfect repayment history → Credit History Score = 100
        return 1.0;
    }
}
