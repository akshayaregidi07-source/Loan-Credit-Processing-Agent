package com.techvestai.project.agent;

import com.techvestai.project.entity.Document;

/**
 * Strategy interface for extracting structured data from uploaded documents.
 *
 * <p>A stub implementation ({@link StubDocumentParser}) is active by default.
 * A full OCR/PDF implementation can be provided as an alternative Spring bean
 * without changing any agent code.
 *
 * <p><b>Requirements:</b> 3.5, 3.6, 12.1
 */
public interface DocumentParser {

    /**
     * Returns the applicant name embedded in the document, or {@code null} if
     * the parser cannot determine it (stub always returns a value derived from
     * the filename so pipeline tests pass without real OCR).
     *
     * @param document the {@link Document} entity whose {@code storagePath}
     *                 points to the uploaded file
     * @return extracted name, or {@code null}
     */
    String extractApplicantName(Document document);

    /**
     * Returns the date-of-birth string (ISO-8601 date) embedded in the
     * document, or {@code null}.
     *
     * @param document the uploaded document entity
     * @return ISO-8601 date string, or {@code null}
     */
    String extractDateOfBirth(Document document);

    /**
     * Returns the gross monthly income value extracted from the document,
     * or {@code null}.
     *
     * @param document the uploaded document entity
     * @return income as a string representation of a decimal, or {@code null}
     */
    String extractGrossMonthlyIncome(Document document);

    /**
     * Returns the number of consecutive months of confirmed income found in
     * the document, or {@code -1} if not determinable.
     *
     * @param document the uploaded document entity
     * @return number of months, or {@code -1}
     */
    int extractConsecutiveIncomeMonths(Document document);

    /**
     * Returns the on-time repayment ratio (0.0–1.0) found in the bank
     * statement, or {@code -1.0} if not determinable.
     *
     * @param document the uploaded document entity
     * @return ratio between 0.0 and 1.0, or {@code -1.0}
     */
    double extractOnTimeRepaymentRatio(Document document);
}
