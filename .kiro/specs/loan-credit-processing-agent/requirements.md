# Requirements Document

## Introduction

This document defines the requirements for the **AI-powered Loan / Credit Application Processing Agent** built on top of the existing TechVest AI platform (Spring Boot 3.2 / Java 21 backend, React 19 + Vite + TypeScript frontend, PostgreSQL database). The system orchestrates five specialised agents — Document Validation Agent, Credit Policy Agent, Recommendation Agent, Fairness Agent, and Audit Agent — to automate the preliminary assessment of loan and credit applications while keeping a human underwriter as the sole decision-maker. Every processing step is recorded in a tamper-evident audit trail to satisfy regulatory and explainability obligations.

---

## Glossary

- **Applicant**: A natural person who submits a loan or credit application through the system.
- **Application**: A loan or credit request submitted by an Applicant, consisting of application form data and one or more supporting documents.
- **Document_Validation_Agent**: The agent responsible for verifying the presence, format, and internal consistency of all documents attached to an Application.
- **Credit_Policy_Agent**: The agent responsible for computing a transparent Credit Score from verified financial data using defined policy rules.
- **Recommendation_Agent**: The agent responsible for producing an Approve, Refer, or Decline recommendation based on Credit Score and policy thresholds.
- **Fairness_Agent**: The agent responsible for re-evaluating the Credit Score after stripping protected identity attributes to detect and flag discriminatory outcomes.
- **Audit_Agent**: The agent responsible for persisting every agent action, input, output, and timestamp to the Audit Log.
- **Underwriter**: An authenticated human user with the UNDERWRITER role who reviews agent outputs and records the final decision.
- **Credit_Score**: A numeric value in the range 0–1000 calculated by the Credit_Policy_Agent from Debt-to-Income Ratio, Income Stability Score, and Credit History Score.
- **Debt_to_Income_Ratio (DTI)**: Total monthly debt obligations divided by gross monthly income, expressed as a decimal.
- **Income_Stability_Score**: A numeric sub-score (0–100) derived from employment duration and income consistency in the provided bank statements.
- **Credit_History_Score**: A numeric sub-score (0–100) derived from repayment history and outstanding credit obligations.
- **Recommendation**: One of three enumerated values — APPROVE, REFER, or DECLINE — produced by the Recommendation_Agent.
- **Fairness_Delta**: The absolute difference between the original Credit Score and the anonymised Credit Score computed by the Fairness_Agent.
- **Audit_Log**: An append-only, timestamped record stored in PostgreSQL that captures every agent action and the final Underwriter decision for a given Application.
- **Protected_Attribute**: Applicant identity fields (name, national ID number, gender, date of birth, ethnicity, address) that are removed before Fairness_Agent scoring.
- **JWT**: JSON Web Token used for stateless authentication of API requests.
- **API_Gateway**: The Spring Boot REST layer that receives client requests, enforces authentication, and routes requests to the appropriate agent service.
- **Document**: A file uploaded by the Applicant. Supported types: Government-issued ID, Income Proof, Bank Statement.
- **Policy_Threshold**: Configurable numeric boundaries stored in the database that define Approve (Credit_Score ≥ 700), Refer (500 ≤ Credit_Score < 700), and Decline (Credit_Score < 500) bands.

---

## Requirements

---

### Requirement 1: User Authentication and Role-Based Access

**User Story:** As an Underwriter or system administrator, I want to authenticate via a secure login and access only the features my role permits, so that sensitive applicant data and decision controls are protected.

#### Acceptance Criteria

1. THE API_Gateway SHALL accept login requests containing a username and password and return a signed JWT on successful authentication.
2. WHEN an API request is received without a valid JWT, THE API_Gateway SHALL reject the request with HTTP 401.
3. WHEN an API request carries a JWT whose expiry timestamp is in the past, THE API_Gateway SHALL reject the request with HTTP 401.
4. WHEN an authenticated user attempts to access an endpoint that requires a role the user does not hold, THE API_Gateway SHALL reject the request with HTTP 403, confirming that the user is authenticated but not authorised for the requested resource.
5. THE API_Gateway SHALL support the following roles: APPLICANT, UNDERWRITER, and ADMIN.
6. WHERE the ADMIN role is present, THE API_Gateway SHALL permit access to user management, policy threshold configuration, and audit log export endpoints.
7. WHERE the UNDERWRITER role is present, THE API_Gateway SHALL permit access to application review, decision recording, and audit log read endpoints.
8. WHERE the APPLICANT role is present, THE API_Gateway SHALL permit access to application submission and own application status endpoints only.

---

### Requirement 2: Loan Application Submission

**User Story:** As an Applicant, I want to submit my loan application together with all required supporting documents, so that the system can begin processing my request.

#### Acceptance Criteria

1. WHEN an authenticated APPLICANT submits an application form with required fields (requested loan amount, loan purpose, employment status, gross monthly income, total monthly debt obligations), THE API_Gateway SHALL create an Application record with status SUBMITTED and return the assigned application identifier.
2. IF the submitted application form is missing any required field, THEN THE API_Gateway SHALL return HTTP 422 with a field-level error message identifying each missing field.
3. WHEN an Application is created, THE API_Gateway SHALL accept multipart file uploads for Government-issued ID, Income Proof, and Bank Statement documents associated with that Application.
4. IF an uploaded file exceeds 10 MB, THEN THE API_Gateway SHALL reject the file and return HTTP 413 with a message stating the maximum allowed size.
5. IF an uploaded file has a MIME type other than application/pdf, image/jpeg, or image/png, THEN THE API_Gateway SHALL reject the file and return HTTP 415 with a message stating the accepted types.
6. THE API_Gateway SHALL store each accepted Document in the configured file storage location and record the Document metadata (filename, MIME type, file size, storage path, upload timestamp) in the database linked to the Application.
7. WHEN an Application is created, THE Audit_Agent SHALL record an APPLICATION_SUBMITTED event containing the application identifier, applicant identifier, requested amount, and submission timestamp.

---

### Requirement 3: Document Validation Agent

**User Story:** As an Underwriter, I want the system to automatically verify that all required documents are present and internally consistent, so that I only review applications with complete and coherent evidence.

#### Acceptance Criteria

1. WHEN an Application moves to SUBMITTED status, THE Document_Validation_Agent SHALL verify that all three required Document types (Government-issued ID, Income Proof, Bank Statement) are present.
2. IF one or more required Document types are absent, THEN THE Document_Validation_Agent SHALL set the Application status to DOCUMENT_INCOMPLETE, record the list of missing document types, and halt further agent processing.
3. WHEN all required Documents are present, THE Document_Validation_Agent SHALL validate each Document for file integrity (non-zero byte count, readable format, absence of corruption indicators).
4. IF a Document fails integrity validation, THEN THE Document_Validation_Agent SHALL set the Application status to DOCUMENT_INVALID, record the specific Document identifier and failure reason, and halt further agent processing.
5. WHEN all Documents pass integrity validation, THE Document_Validation_Agent SHALL extract and cross-validate the applicant name and date of birth between the Government-issued ID and the Income Proof.
6. IF the applicant name extracted from the Income Proof does not match the name on the Government-issued ID, THEN THE Document_Validation_Agent SHALL flag the Application with a CONSISTENCY_MISMATCH finding and set the status to DOCUMENT_INCONSISTENT.
7. WHEN all validation steps — presence check, integrity validation, and cross-document consistency check — pass without any failure, THE Document_Validation_Agent SHALL set the Application status to DOCUMENTS_VERIFIED and emit a DOCUMENTS_VERIFIED event to trigger the Credit_Policy_Agent; partial pass is not sufficient.
8. WHEN any Document validation step completes (pass or fail), THE Audit_Agent SHALL record a DOCUMENT_VALIDATION_RESULT event containing the Application identifier, validation step name, outcome, and timestamp.

---

### Requirement 4: Credit Policy Agent — Credit Score Calculation

**User Story:** As an Underwriter, I want the system to calculate a transparent, rules-based Credit Score from verified financial data, so that I can understand exactly how the score was derived.

#### Acceptance Criteria

1. WHEN an Application reaches DOCUMENTS_VERIFIED status, THE Credit_Policy_Agent SHALL compute the Debt_to_Income_Ratio by dividing the Applicant's total monthly debt obligations by gross monthly income.
2. IF the Applicant's gross monthly income is zero, THEN THE Credit_Policy_Agent SHALL set the Application status to SCORING_ERROR with reason ZERO_INCOME and halt further agent processing.
3. WHEN the Debt_to_Income_Ratio is computed, THE Credit_Policy_Agent SHALL derive a DTI sub-score: DTI ≤ 0.20 maps to 100, 0.21–0.35 maps to 80, 0.36–0.43 maps to 60, 0.44–0.50 maps to 40, and DTI > 0.50 maps to 0.
4. WHEN both the Income Proof and the Bank Statement documents are verified, THE Credit_Policy_Agent SHALL compute the Income_Stability_Score as a value between 0 and 100 (capped at 100) based on the number of consecutive months of confirmed income (≥ 24 months maps to 100, 12–23 months maps to 70, 6–11 months maps to 40, < 6 months maps to 0).
5. WHEN Credit History data is available in the verified documents, THE Credit_Policy_Agent SHALL compute the Credit_History_Score as a value between 0 and 100 (capped at 100) based on the on-time repayment ratio found in the Bank Statement (ratio ≥ 0.95 maps to 100, 0.80–0.94 maps to 75, 0.65–0.79 maps to 50, < 0.65 maps to 20).
6. THE Credit_Policy_Agent SHALL compute the final Credit_Score using the weighted formula: Credit_Score = (DTI_sub_score × 0.40) + (Income_Stability_Score × 0.35) + (Credit_History_Score × 0.25), scaled to the range 0–1000.
7. WHEN the Credit_Score is computed, THE Credit_Policy_Agent SHALL persist the Credit_Score alongside each sub-score, its weight, and the intermediate value to the Application record.
8. WHEN scoring completes, THE Audit_Agent SHALL record a CREDIT_SCORE_COMPUTED event containing the Application identifier, Credit_Score, all sub-scores, weights, and computation timestamp.

---

### Requirement 5: Recommendation Agent

**User Story:** As an Underwriter, I want the system to produce a clear Approve, Refer, or Decline recommendation with policy citations, so that I can make an informed final decision efficiently.

#### Acceptance Criteria

1. WHEN the Credit_Policy_Agent has computed a Credit_Score, THE Recommendation_Agent SHALL evaluate the score against the active Policy_Threshold record and SHALL produce a Recommendation that matches the score band; Recommendations that contradict the computed Credit_Score and active thresholds are not permitted.
2. WHEN the Credit_Score is greater than or equal to the APPROVE threshold (default 700), THE Recommendation_Agent SHALL set the Application Recommendation to APPROVE.
3. WHEN the Credit_Score is greater than or equal to the REFER threshold (default 500) and less than the APPROVE threshold, THE Recommendation_Agent SHALL set the Application Recommendation to REFER.
4. WHEN the Credit_Score is less than the REFER threshold (default 500), THE Recommendation_Agent SHALL set the Application Recommendation to DECLINE.
5. WHEN a Recommendation is set, THE Recommendation_Agent SHALL attach the identifier and text of the Policy_Threshold record used, so that the justification is traceable to a specific policy version.
6. THE Recommendation_Agent SHALL include a human-readable explanation listing each scoring factor, its value, its weight, and its contribution to the overall Credit_Score within the Recommendation output.
7. WHEN the Recommendation is produced, THE Audit_Agent SHALL record a RECOMMENDATION_PRODUCED event containing the Application identifier, Recommendation value, Credit_Score, Policy_Threshold identifier, and timestamp.

---

### Requirement 6: Fairness Agent — Bias Detection

**User Story:** As a compliance officer, I want the system to re-score each application after removing protected identity attributes, so that I can detect and flag any demographically driven bias before an Underwriter reviews the case.

#### Acceptance Criteria

1. WHEN the Recommendation_Agent has produced a Recommendation, THE Fairness_Agent SHALL create an anonymised copy of the Application's financial inputs by removing all Protected_Attributes.
2. THE Fairness_Agent SHALL re-execute the Credit_Policy_Agent scoring logic against the anonymised Application inputs to produce an anonymised Credit_Score.
3. THE Fairness_Agent SHALL compute the Fairness_Delta as the absolute difference between the original Credit_Score and the anonymised Credit_Score.
4. WHEN the Fairness_Delta is greater than 50 points, THE Fairness_Agent SHALL attach a FAIRNESS_FLAG to the Application and set a flag reason of POTENTIAL_BIAS_DETECTED. WHEN the Fairness_Delta is exactly 50 points, THE Fairness_Agent SHALL also attach a FAIRNESS_FLAG with reason POTENTIAL_BIAS_DETECTED.
5. WHEN the Fairness_Delta is less than 50 points, THE Fairness_Agent SHALL record the result as FAIRNESS_PASSED.
6. THE Fairness_Agent SHALL NOT modify the original Credit_Score, Recommendation, or any Application data; the anonymised score and delta are stored separately for review purposes.
7. WHEN Fairness evaluation completes, THE Audit_Agent SHALL record a FAIRNESS_EVALUATION_COMPLETED event containing the Application identifier, original Credit_Score, anonymised Credit_Score, Fairness_Delta, fairness outcome (FAIRNESS_FLAG or FAIRNESS_PASSED), and timestamp.
8. WHEN a FAIRNESS_FLAG is present on an Application, THE API_Gateway SHALL surface the flag and its reason to the Underwriter in the application review response.

---

### Requirement 7: Underwriter Review and Final Decision

**User Story:** As an Underwriter, I want to review all agent outputs for an application and record my final decision with justification, so that human judgment always governs the outcome.

#### Acceptance Criteria

1. WHEN an Application has received a Recommendation from the Recommendation_Agent and a fairness result from the Fairness_Agent, THE API_Gateway SHALL set the Application status to AWAITING_UNDERWRITER_REVIEW.
2. WHEN an authenticated UNDERWRITER retrieves an Application in AWAITING_UNDERWRITER_REVIEW status, THE API_Gateway SHALL return the application form data, Document metadata, Credit_Score, all sub-scores with weights, Recommendation, Policy_Threshold citations, and the Fairness evaluation result in a single response payload.
3. THE API_Gateway SHALL accept a decision submission from an authenticated UNDERWRITER containing a decision value (APPROVED, DECLINED, or REFERRED_FOR_FURTHER_REVIEW), a mandatory justification text of at least 20 characters, and an optional override reason when the decision differs from the system Recommendation.
4. IF the UNDERWRITER submits a decision without a justification text of at least 20 characters, THEN THE API_Gateway SHALL reject the submission with HTTP 422 and a message stating the minimum justification length requirement.
5. WHEN a valid decision submission is received, THE API_Gateway SHALL update the Application status to DECISION_RECORDED only after both validation and submission succeed, and SHALL persist the decision value, justification text, override reason (if present), Underwriter user identifier, and decision timestamp atomically.
6. WHEN the final decision is recorded, THE Audit_Agent SHALL record a FINAL_DECISION_RECORDED event containing the Application identifier, decision value, justification text (first 500 characters), override reason, Underwriter identifier, system Recommendation, and timestamp.
7. WHILE an Application is in DECISION_RECORDED status, THE API_Gateway SHALL prevent modification of the application record, decision, Credit_Score, or Recommendation.

---

### Requirement 8: Audit Log and Decision History

**User Story:** As a compliance officer or administrator, I want a complete, tamper-evident history of every agent action and human decision for each application, so that the organisation can satisfy regulatory audit requirements.

#### Acceptance Criteria

1. THE Audit_Agent SHALL persist every audit event to the audit_events table in PostgreSQL as an append-only record containing: event_id (UUID), application_id, event_type, event_payload (JSON), actor (agent name or user identifier), and created_at (UTC timestamp).
2. THE Audit_Agent SHALL NOT permit updates or deletes on any audit_events row after insertion.
3. WHEN an authenticated UNDERWRITER or ADMIN requests the audit trail for a given Application, THE API_Gateway SHALL return all audit events for that Application ordered by created_at ascending.
4. WHERE the ADMIN role is present, THE API_Gateway SHALL provide an endpoint to export audit events for a date range as a JSON document.
5. WHEN an audit event is persisted, THE Audit_Agent SHALL ensure the event_id is a globally unique UUID generated at insertion time.
6. THE Audit_Agent SHALL record the following event types at minimum: APPLICATION_SUBMITTED, DOCUMENT_VALIDATION_RESULT, CREDIT_SCORE_COMPUTED, RECOMMENDATION_PRODUCED, FAIRNESS_EVALUATION_COMPLETED, FINAL_DECISION_RECORDED.

---

### Requirement 9: Policy Threshold Management

**User Story:** As an ADMIN, I want to configure and version the credit score thresholds that drive Approve, Refer, and Decline decisions, so that policy changes are traceable and do not silently alter historic decisions.

#### Acceptance Criteria

1. THE API_Gateway SHALL provide an endpoint accessible to authenticated ADMIN users to create a new Policy_Threshold record specifying the APPROVE threshold and the REFER threshold as integer values in the range 0–1000.
2. IF the submitted APPROVE threshold is less than or equal to the submitted REFER threshold, THEN THE API_Gateway SHALL reject the request with HTTP 422 and a message stating that the APPROVE threshold must be greater than the REFER threshold.
3. WHEN a new Policy_Threshold record is created, THE API_Gateway SHALL mark it as the ACTIVE policy and set the previously ACTIVE record to SUPERSEDED.
4. THE Credit_Policy_Agent and Recommendation_Agent SHALL always evaluate applications against the ACTIVE Policy_Threshold record at the time of processing.
5. WHEN a Policy_Threshold record is set to SUPERSEDED, THE API_Gateway SHALL preserve the record in the database and prevent deletion.
6. THE API_Gateway SHALL provide an endpoint accessible to authenticated ADMIN and UNDERWRITER users to list all Policy_Threshold records ordered by creation timestamp descending, indicating which record is ACTIVE.

---

### Requirement 10: Application Status Tracking

**User Story:** As an Applicant, I want to check the current status of my submitted application, so that I know what stage of processing it is at without needing to contact the lender.

#### Acceptance Criteria

1. WHEN an authenticated APPLICANT requests the status of an Application identified by its application identifier, THE API_Gateway SHALL return the current Application status and the last status update timestamp.
2. THE API_Gateway SHALL expose the following status values to APPLICANT-role users: SUBMITTED, DOCUMENT_INCOMPLETE, DOCUMENT_INVALID, DOCUMENT_INCONSISTENT, PROCESSING, AWAITING_UNDERWRITER_REVIEW, DECISION_RECORDED.
3. IF an APPLICANT requests the status of an Application that does not belong to that APPLICANT, THEN THE API_Gateway SHALL return HTTP 404 with no application data, including no decision details.
4. WHEN the Application status is DECISION_RECORDED, THE API_Gateway SHALL include the final decision value (APPROVED, DECLINED, or REFERRED_FOR_FURTHER_REVIEW) and the decision timestamp in the status response for the owning APPLICANT; the decision details SHALL be returned regardless of whether the status field itself is unavailable.
5. THE API_Gateway SHALL NOT expose Credit_Score, sub-scores, Fairness evaluation details, internal agent logs, or Underwriter justification text to APPLICANT-role users.

---

### Requirement 11: Frontend Application Portal

**User Story:** As an Applicant or Underwriter, I want an accessible, responsive web interface to submit applications, upload documents, and review processing results, so that I can interact with the system without technical knowledge of the underlying APIs.

#### Acceptance Criteria

1. THE Frontend SHALL provide a login form that collects username and password, submits credentials to the API_Gateway authentication endpoint, and stores the returned JWT in browser memory (not localStorage or sessionStorage) for the duration of the session.
2. WHEN a stored JWT expires, THE Frontend SHALL redirect the user to the login page and clear the expired token from memory.
3. WHERE the APPLICANT role is active, THE Frontend SHALL display an application submission form with fields for requested loan amount, loan purpose, employment status, gross monthly income, and total monthly debt obligations.
4. WHERE the APPLICANT role is active, THE Frontend SHALL provide a document upload interface that accepts Government-issued ID, Income Proof, and Bank Statement files and enforces the 10 MB per-file size limit and the accepted MIME types (PDF, JPEG, PNG) before submission.
5. WHERE the APPLICANT role is active, THE Frontend SHALL display the current Application status and, when status is DECISION_RECORDED, display the final decision value and timestamp.
6. WHERE the UNDERWRITER role is active, THE Frontend SHALL display a paginated worklist of Applications in AWAITING_UNDERWRITER_REVIEW status.
7. WHERE the UNDERWRITER role is active, THE Frontend SHALL display a detailed application review screen showing application form data, Document metadata, Credit_Score, sub-scores with weights, Recommendation, Policy_Threshold citations, Fairness evaluation result, and any FAIRNESS_FLAG with its reason.
8. WHERE the UNDERWRITER role is active, THE Frontend SHALL provide a decision submission form accepting decision value, mandatory justification text, and optional override reason, and SHALL validate that the justification text is at least 20 characters before enabling form submission.
9. THE Frontend SHALL be accessible in conformance with WCAG 2.1 Level AA, including keyboard navigability, sufficient colour contrast, and ARIA labels on all interactive elements.
10. WHEN an API_Gateway request returns an error response, THE Frontend SHALL display a user-readable error message corresponding to the error without exposing raw HTTP status codes or stack traces to the user.

---

### Requirement 12: Parser and Data Extraction — Round-Trip Integrity

**User Story:** As a system operator, I want all structured data extracted from documents and serialised between agents to be verifiable for integrity, so that no data loss or corruption occurs during internal processing.

#### Acceptance Criteria

1. WHEN the Document_Validation_Agent extracts structured data (applicant name, date of birth, income figure, repayment ratio, employment months) from a Document, THE Document_Validation_Agent SHALL serialise the extracted fields to a JSON payload stored in the Application record.
2. THE Credit_Policy_Agent SHALL deserialise the JSON extraction payload produced by the Document_Validation_Agent and use only those deserialised values for scoring.
3. FOR ALL valid extraction JSON payloads produced by the Document_Validation_Agent, deserialising then re-serialising the payload SHALL produce a JSON document that is semantically equivalent to the original (round-trip property).
4. WHEN an extraction payload fails deserialisation, THE Credit_Policy_Agent SHALL set the Application status to SCORING_ERROR with reason PAYLOAD_DESERIALISATION_FAILURE and halt further processing.
5. WHEN a deserialisation failure occurs, THE Audit_Agent SHALL record a DESERIALISATION_FAILURE event containing the Application identifier, agent name, and failure detail.

---

### Requirement 13: Security and Data Protection

**User Story:** As a data protection officer, I want all applicant data to be handled securely throughout the system, so that the organisation complies with data protection regulations.

#### Acceptance Criteria

1. THE API_Gateway SHALL enforce HTTPS for all client-facing endpoints in production deployments.
2. WHEN storing applicant Documents, THE API_Gateway SHALL ensure that file storage paths are not predictable or enumerable by Applicant identifiers alone.
3. THE API_Gateway SHALL always validate and sanitise all user-supplied input before passing it to backend services or database queries, using parameterised queries for all database interactions; this validation capability SHALL be active at all times, not conditionally.
4. WHEN a JWT secret is not supplied via environment variable at startup, THE API_Gateway SHALL refuse to start and log an error message stating that a JWT secret is required.
5. THE Fairness_Agent SHALL process anonymised Application inputs in memory only and SHALL NOT persist Protected_Attributes to any log, database table, or file during the anonymisation step.
6. WHEN an API_Gateway endpoint receives more than 100 requests per minute from the same authenticated user, THE API_Gateway SHALL respond with HTTP 429 and a Retry-After header.
