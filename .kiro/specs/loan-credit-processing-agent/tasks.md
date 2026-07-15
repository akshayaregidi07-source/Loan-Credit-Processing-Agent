# Implementation Plan: AI-Powered Loan / Credit Application Processing Agent

## Overview

Implement the full agent pipeline on the existing TechVest AI platform (Spring Boot 3.2 / Java 21 backend, React 19 + Vite + TypeScript + Tailwind CSS frontend, PostgreSQL). The plan follows six logical layers — Foundation → Backend Core → Agent Pipeline → API Layer → Frontend → Cross-Cutting Testing — ensuring each task builds on the previous with no orphaned code. All 23 correctness properties from the design are covered by property-based test sub-tasks using jqwik (backend) and fast-check (frontend).

---

## Tasks

- [x] 1. Project foundation — dependencies, migrations, and shared infrastructure
  - [x] 1.1 Add missing Maven dependencies to `pom.xml`
    - Add `flyway-core` and `flyway-database-postgresql` for database migrations
    - Add `jqwik` (net.jqwik:jqwik:1.8.4) in test scope for property-based tests
    - Add `spring-boot-starter-cache` for rate-limit counter support
    - Add `spring-boot-testcontainers` and `testcontainers-postgresql` in test scope for integration tests
    - Ensure `springdoc-openapi-starter-webmvc-ui` 2.2.0 is present (already in pom.xml — verify)
    - Remove `spring.jpa.hibernate.ddl-auto: update` from `application.yml` (Flyway takes over schema)
    - _Requirements: 1.1, 2.6, 8.1, 13.1_

  - [x] 1.2 Create Flyway baseline migration — `V1__baseline_users.sql`
    - Alter the existing `users` table: add `role VARCHAR(20) NOT NULL DEFAULT 'APPLICANT'`, `created_at TIMESTAMP NOT NULL DEFAULT NOW()`, `enabled BOOLEAN NOT NULL DEFAULT TRUE`
    - Create migration file at `src/main/resources/db/migration/V1__baseline_users.sql`
    - _Requirements: 1.5_

  - [x] 1.3 Create Flyway migration for core domain tables — `V2__create_core_tables.sql`
    - Create `applications`, `documents`, `policy_thresholds`, `document_extraction_payloads` tables with all columns, constraints, and indexes per the ERD in the design
    - Use `UUID` primary keys (DEFAULT gen_random_uuid()); require `pgcrypto` extension at top of script
    - _Requirements: 2.1, 2.6, 3.1, 4.1, 9.1_

  - [x] 1.4 Create Flyway migration for scoring and decision tables — `V3__create_scoring_tables.sql`
    - Create `credit_scores`, `recommendations`, `fairness_results`, `underwriter_decisions` tables with all columns, FK constraints, and unique indexes per the ERD
    - _Requirements: 4.7, 5.5, 6.6, 7.5_

  - [x] 1.5 Create Flyway migration for audit log — `V4__create_audit_events.sql`
    - Create `audit_events` table with `JSONB event_payload` column; add a row-level security policy or trigger that prevents UPDATE and DELETE on this table
    - _Requirements: 8.1, 8.2_

  - [x] 1.6 Create Flyway migration for default policy threshold — `V5__seed_default_policy_threshold.sql`
    - Insert a default ACTIVE `policy_thresholds` row: approve_threshold = 700, refer_threshold = 500
    - _Requirements: 9.1, 9.3_


- [x] 2. Backend core — entities, enums, and repositories
  - [x] 2.1 Define shared enums and extend the `User` entity
    - Create enums: `UserRole` (APPLICANT, UNDERWRITER, ADMIN), `ApplicationStatus`, `DocumentType`, `DocumentValidationStatus`, `RecommendationValue`, `DecisionValue`, `FairnessOutcome`, `PolicyThresholdStatus`, `AuditEventType`
    - Extend `User.java`: add `role UserRole`, `createdAt Instant`, `enabled boolean`; add `@Enumerated(EnumType.STRING)` on role
    - _Requirements: 1.5, 2.1_

  - [x] 2.2 Create `Application` JPA entity
    - Map all columns from the `applications` table; use `@GeneratedValue` with UUID strategy; add `@Enumerated(EnumType.STRING)` on `status`; add `@ManyToOne` to `User` (applicant)
    - _Requirements: 2.1, 10.1_

  - [x] 2.3 Create `Document` JPA entity
    - Map all columns from the `documents` table; `@ManyToOne` to `Application`; `@Enumerated` on `documentType` and `validationStatus`
    - _Requirements: 2.3, 3.1_

  - [x] 2.4 Create `PolicyThreshold`, `CreditScore`, `Recommendation`, `FairnessResult`, `UnderwriterDecision`, and `DocumentExtractionPayload` JPA entities
    - Each entity maps its table per the ERD; use `@OneToOne` where the design marks FK UK (unique); use `@Enumerated(EnumType.STRING)` on all enum columns
    - `AuditEvent.java`: map `event_payload` as `@JdbcTypeCode(SqlTypes.JSON) Map<String, Object>`
    - _Requirements: 4.7, 5.5, 6.3, 7.5, 8.1, 9.1, 12.1_

  - [x] 2.5 Create Spring Data JPA repositories
    - `ApplicationRepository`: add `findByIdAndApplicantId`, `findByStatus`, `findAllByStatus` (pageable)
    - `DocumentRepository`: `findByApplicationIdAndDocumentType`
    - `AuditEventRepository`: `findByApplicationIdOrderByCreatedAtAsc`, `findByCreatedAtBetween`
    - `PolicyThresholdRepository`: `findByStatus`, `findAllByOrderByCreatedAtDesc`
    - `CreditScoreRepository`, `RecommendationRepository`, `FairnessResultRepository`, `UnderwriterDecisionRepository`, `DocumentExtractionPayloadRepository` — standard CRUD
    - _Requirements: 2.6, 8.3, 9.4, 9.6_


- [ ] 3. Security layer — JWT, RBAC, rate limiting
  - [x] 3.1 Implement `JwtConfig` startup secret validation
    - Create `JwtConfig.java` in `config/`; use `@PostConstruct` to read `JWT_SECRET` env var; throw `IllegalStateException` if absent or shorter than 32 characters
    - Update `application.yml`: remove hardcoded secret; read from `${JWT_SECRET}` placeholder
    - _Requirements: 13.4_

  - [ ]* 3.2 Write property test for JWT expiry rejection (Property 1)
    - **Property 1: JWT Expiry Rejection**
    - Create `JwtTokenProviderPropertyTest.java`; use `@Property(tries = 100)` + `@ForAll` timestamps in the past; call `JwtTokenProvider.validateToken()` and assert false
    - **Validates: Requirements 1.3**

  - [-] 3.3 Implement `JwtTokenProvider` and `JwtAuthenticationFilter`
    - `JwtTokenProvider.java`: `generateToken(UserDetails)`, `validateToken(String)`, `extractUsername(String)` using jjwt 0.11.5
    - `JwtAuthenticationFilter.java`: extend `OncePerRequestFilter`; extract Bearer token, validate, set `SecurityContextHolder`
    - `UserPrincipal.java`: wrap `User` entity; implement `UserDetails`
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ] 3.4 Implement `SecurityConfig` with RBAC and endpoint protection
    - `SecurityConfig.java`: configure `SecurityFilterChain`; permit `/api/v1/auth/**`; secure all other routes; wire `JwtAuthenticationFilter`; disable CSRF for REST; configure CORS for `localhost:5173`
    - Enforce role-based access per the RBAC matrix in the design for all endpoint patterns
    - _Requirements: 1.2, 1.4, 1.5, 1.6, 1.7, 1.8_

  - [ ]* 3.5 Write property test for role-based access denial (Property 2)
    - **Property 2: Role-Based Access Denial**
    - Create `RbacPropertyTest.java`; generate random (role, endpoint) pairs where role is insufficient; assert HTTP 403
    - **Validates: Requirements 1.4**

  - [ ] 3.6 Implement `RateLimitService` and rate-limit filter
    - `RateLimitService.java`: `ConcurrentHashMap<userId, Deque<Instant>>`; sliding 60-second window; `isAllowed(userId)` prunes old entries and returns false when count > 100
    - Wire as a filter applied after JWT authentication; on rejection return HTTP 429 with `Retry-After` header
    - _Requirements: 13.6_

  - [ ]* 3.7 Write property test for rate limiting (Property 21)
    - **Property 21: Rate Limiting Enforced at > 100 Requests per Minute**
    - Create `RateLimitServicePropertyTest.java`; generate user IDs; simulate 101+ requests within window; assert 101st call returns false / HTTP 429 with Retry-After header
    - **Validates: Requirements 13.6**


- [ ] 4. DTOs, exception handling, and OpenAPI configuration
  - [-] 4.1 Create all request and response DTOs
    - `LoginRequest`, `AuthResponse`
    - `ApplicationSubmitRequest` (with Bean Validation annotations per design), `ApplicationStatusResponse`, `ApplicationReviewResponse`
    - `CreditScoreBreakdown`, `FairnessResultResponse`, `RecommendationDetail`, `DocumentMetadata`, `ApplicationFormData`
    - `UnderwriterDecisionRequest` (with `@Size(min=20)` on justificationText)
    - `PolicyThresholdRequest`, `AuditEventResponse`
    - `DocumentExtractionPayload` (POJO that maps the JSONB fields; must be Jackson-serialisable)
    - _Requirements: 2.1, 2.2, 4.7, 5.6, 7.3, 12.1_

  - [-] 4.2 Implement `GlobalExceptionHandler`
    - `@RestControllerAdvice` class; map all exception types from the design error table to the correct HTTP status codes using RFC 7807 Problem Details format; catch-all maps to 500 with generic message and no stack trace
    - _Requirements: 2.2, 2.4, 2.5, 7.4, 9.2_

  - [x] 4.3 Create custom exception classes
    - `DocumentValidationException`, `ScoringException`, `UnauthorisedResourceException`, `TooManyRequestsException`
    - _Requirements: 3.2, 3.4, 4.2, 12.4_

  - [x] 4.4 Configure `OpenApiConfig` and Swagger UI
    - `OpenApiConfig.java`: configure JWT bearer auth scheme for Swagger UI; set API title, version, and description
    - _Requirements: 1.1_


- [ ] 5. Checkpoint — wire foundation and compile
  - Ensure the project compiles cleanly with `mvn compile`; Flyway migrations run against the configured PostgreSQL instance; Spring context loads; no class-not-found or bean-creation errors. Ask the user if any issues arise.

- [ ] 6. Audit Agent
  - [ ] 6.1 Implement `AuditAgent`
    - `AuditAgent.java` in `agent/`; single public method `recordEvent(AuditEventType, UUID applicationId, String actor, Map<String,Object> payload)`
    - Build an `AuditEvent` entity, generate a UUID event_id, set `created_at` to `Instant.now(ZoneOffset.UTC)`, and save via `AuditEventRepository`
    - This is a synchronous, transactional write; no update or delete paths exposed
    - _Requirements: 8.1, 8.2, 8.5, 8.6_

  - [ ]* 6.2 Write property test for audit event integrity (Property 15)
    - **Property 15: Audit Event Integrity — Structure and Uniqueness**
    - Create `AuditAgentPropertyTest.java`; generate batches of audit event inputs; assert all event_ids are distinct UUIDs, all required fields are non-null, and no update/delete method exists on `AuditEventRepository`
    - **Validates: Requirements 8.1, 8.2, 8.5**


- [ ] 7. Document Validation Agent
  - [ ] 7.1 Implement presence check in `DocumentValidationAgent`
    - `DocumentValidationAgent.java` in `agent/`; `validate(UUID applicationId)` method
    - Query `DocumentRepository` for all three required types (GOVERNMENT_ID, INCOME_PROOF, BANK_STATEMENT); if any missing, set Application status to DOCUMENT_INCOMPLETE, record missing types on Application, call `AuditAgent.recordEvent(DOCUMENT_VALIDATION_RESULT, ...)`, and throw `DocumentValidationException` to halt pipeline
    - _Requirements: 3.1, 3.2, 3.8_

  - [ ] 7.2 Implement file integrity check in `DocumentValidationAgent`
    - For each document, verify: file size > 0 bytes, file is readable from storage path, first bytes match expected magic bytes for the declared MIME type (PDF: `%PDF`, JPEG: `FF D8 FF`, PNG: `89 50 4E 47`)
    - On failure, set status DOCUMENT_INVALID, record document id and failure reason, call audit, throw to halt
    - _Requirements: 3.3, 3.4, 3.8_

  - [ ] 7.3 Implement cross-document consistency validation and payload extraction in `DocumentValidationAgent`
    - Parse applicant name and date of birth from GOVERNMENT_ID and INCOME_PROOF documents (use filename metadata as stub; real extraction reads embedded text from PDF/image metadata — implement as a `DocumentParser` interface with a stub implementation)
    - Compare extracted names; on mismatch set DOCUMENT_INCONSISTENT, record CONSISTENCY_MISMATCH finding, call audit, throw to halt
    - On full pass: serialise extracted fields (applicantName, dateOfBirth, grossMonthlyIncome, consecutiveIncomeMonths, onTimeRepaymentRatio, extractedAt) to `DocumentExtractionPayload` as JSONB; persist to `document_extraction_payloads`; set Application status to DOCUMENTS_VERIFIED; call audit with DOCUMENT_VALIDATION_RESULT PASS event; trigger Credit Policy Agent
    - _Requirements: 3.5, 3.6, 3.7, 3.8, 12.1_

  - [ ]* 7.4 Write property test for document extraction payload round-trip (Property 20)
    - **Property 20: Document Extraction Payload Round-Trip**
    - Create `DocumentExtractionPropertyTest.java`; use `@ForAll` to generate valid `DocumentExtractionPayload` objects; serialise with Jackson ObjectMapper then deserialise; assert all fields semantically equal using `isEqualByComparingTo` for BigDecimal fields
    - **Validates: Requirements 12.3**


- [ ] 8. Credit Policy Agent
  - [ ] 8.1 Implement DTI computation in `CreditPolicyAgent`
    - `CreditPolicyAgent.java` in `agent/`; `score(UUID applicationId)` entry method
    - Deserialise `DocumentExtractionPayload` from JSONB; on failure set SCORING_ERROR (PAYLOAD_DESERIALISATION_FAILURE), call `AuditAgent`, throw `ScoringException`
    - If `grossMonthlyIncome` is zero, set SCORING_ERROR (ZERO_INCOME), call audit, throw
    - Compute DTI = `totalMonthlyDebt / grossMonthlyIncome` using `BigDecimal` with `HALF_UP`, 2 decimal places
    - _Requirements: 4.1, 4.2, 12.2, 12.4, 12.5_

  - [ ]* 8.2 Write property test for DTI computation correctness (Property 5)
    - **Property 5: DTI Computation Correctness**
    - Create `CreditPolicyAgentPropertyTest.java`; generate `grossMonthlyIncome > 0` and `totalMonthlyDebt ≥ 0` with `@ForAll @BigRange`; assert computed DTI equals `totalMonthlyDebt / grossMonthlyIncome` to 2 decimal places
    - **Validates: Requirements 4.1**

  - [ ] 8.3 Implement sub-score band mapping in `CreditPolicyAgent`
    - `computeDtiSubScore(BigDecimal dti)`: band logic per requirements 4.3
    - `computeIncomeStabilityScore(int months)`: band logic per requirements 4.4
    - `computeCreditHistoryScore(BigDecimal repaymentRatio)`: band logic per requirements 4.5
    - All methods return `int`; input is sourced from the deserialised extraction payload
    - _Requirements: 4.3, 4.4, 4.5_

  - [ ]* 8.4 Write property test for sub-score band mapping correctness (Property 6)
    - **Property 6: Sub-Score Band Mapping Correctness**
    - In `CreditPolicyAgentPropertyTest.java`; add `@Property(tries = 500)` test for each of the three band functions; use `@ForAll` with appropriate ranges; assert correct output for every input according to the band definitions
    - **Validates: Requirements 4.3, 4.4, 4.5**

  - [ ] 8.5 Implement weighted Credit Score formula and persistence in `CreditPolicyAgent`
    - Compute: `(dtiSubScore × 0.40 + incomeStabilityScore × 0.35 + creditHistoryScore × 0.25) × 10`, clamp to [0, 1000]
    - Fetch active `PolicyThreshold`; persist `CreditScore` entity with all sub-scores, weights, and `policyThresholdId`; set Application status to PROCESSING; call `AuditAgent.recordEvent(CREDIT_SCORE_COMPUTED, ...)`; trigger Recommendation Agent
    - _Requirements: 4.6, 4.7, 4.8_

  - [ ]* 8.6 Write property test for weighted credit score formula correctness (Property 7)
    - **Property 7: Weighted Credit Score Formula Correctness**
    - In `CreditPolicyAgentPropertyTest.java`; `@Property(tries = 500)`; generate `dtiSubScore`, `incomeStabilityScore`, `creditHistoryScore` all in [0,100]; assert computed score equals expected formula result; assert result is in [0, 1000]
    - **Validates: Requirements 4.6**


- [ ] 9. Recommendation Agent
  - [ ] 9.1 Implement `RecommendationAgent` score-to-recommendation mapping
    - `RecommendationAgent.java` in `agent/`; `recommend(UUID applicationId)` method
    - Fetch active `PolicyThreshold`; evaluate Credit Score: ≥ approveThreshold → APPROVE, ≥ referThreshold → REFER, < referThreshold → DECLINE
    - Build explanation text listing each factor name, value, weight, and contribution; persist `Recommendation` entity with policyThresholdId and explanation; call `AuditAgent.recordEvent(RECOMMENDATION_PRODUCED, ...)` ; trigger Fairness Agent
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [ ]* 9.2 Write property test for recommendation matching active policy threshold (Property 8)
    - **Property 8: Recommendation Matches Active Policy Threshold**
    - Create `RecommendationAgentPropertyTest.java`; `@Property(tries = 500)`; generate credit score S and valid threshold pair (approveThreshold > referThreshold in [0,1000]); assert correct recommendation per band boundaries; assert no value outside {APPROVE, REFER, DECLINE}
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4**

  - [ ]* 9.3 Write property test for recommendation explanation completeness (Property 9)
    - **Property 9: Recommendation Explanation Contains All Required Fields**
    - In `RecommendationAgentPropertyTest.java`; generate valid credit score inputs; assert explanation string contains factor name, value, weight, and contribution for all three factors (DTI, Income Stability, Credit History)
    - **Validates: Requirements 5.6**


- [ ] 10. Fairness Agent
  - [ ] 10.1 Implement anonymised re-scoring in `FairnessAgent`
    - `FairnessAgent.java` in `agent/`; `evaluate(UUID applicationId)` method
    - Build transient `FinancialInputs` object from only the financial fields (grossMonthlyIncome, totalMonthlyDebt, consecutiveIncomeMonths, onTimeRepaymentRatio); deliberately exclude all Protected Attributes — no persistence of the anonymised object
    - Re-execute the Credit Policy Agent scoring logic (call the same computation methods) on the anonymised inputs to produce `anonymisedCreditScore`; do NOT persist Protected Attributes at any point
    - _Requirements: 6.1, 6.2, 13.5_

  - [ ] 10.2 Implement fairness delta computation and flag logic in `FairnessAgent`
    - Compute `fairnessDelta = |originalCreditScore − anonymisedCreditScore|` using `BigDecimal.abs()`
    - If delta ≥ 50 → `fairnessOutcome = FAIRNESS_FLAG`, flagReason = "POTENTIAL_BIAS_DETECTED"
    - If delta < 50 → `fairnessOutcome = FAIRNESS_PASSED`
    - Persist `FairnessResult` entity; original creditScore and Recommendation entity MUST remain unchanged; call `AuditAgent.recordEvent(FAIRNESS_EVALUATION_COMPLETED, ...)` ; set Application status to AWAITING_UNDERWRITER_REVIEW
    - _Requirements: 6.3, 6.4, 6.5, 6.6, 6.7_

  - [ ]* 10.3 Write property test for fairness delta absolute difference (Property 10)
    - **Property 10: Fairness Delta Is Absolute Difference**
    - Create `FairnessAgentPropertyTest.java`; `@Property(tries = 500)`; generate original and anonymised scores; assert delta = abs(original − anonymised) and delta ≥ 0
    - **Validates: Requirements 6.3**

  - [ ]* 10.4 Write property test for fairness flag threshold (Property 11)
    - **Property 11: Fairness Flag Threshold (≥ 50 triggers flag)**
    - In `FairnessAgentPropertyTest.java`; `@Property(tries = 500)`; generate deltas across [0, 1000]; assert d ≥ 50 → FAIRNESS_FLAG, d < 50 → FAIRNESS_PASSED; assert boundary d = 50 triggers FAIRNESS_FLAG
    - **Validates: Requirements 6.4, 6.5**

  - [ ]* 10.5 Write property test for fairness agent does not mutate original scores (Property 12)
    - **Property 12: Fairness Agent Does Not Mutate Original Scores**
    - In `FairnessAgentPropertyTest.java`; snapshot original creditScore and recommendation before running agent; run agent; assert both values unchanged after completion
    - **Validates: Requirements 6.6**

- [ ] 11. Checkpoint — verify agent pipeline end-to-end
  - Run all agent unit tests; run a manual integration trace (or Testcontainers integration test) that feeds a test application through DVA → CPA → RA → FA and verifies all five database records are created and all six audit event types are present. Ask the user if any issues arise.


- [ ] 12. Service layer — application, document, policy threshold, user management
  - [ ] 12.1 Implement `ApplicationService`
    - `ApplicationService.java` in `service/`
    - `submitApplication(ApplicationSubmitRequest, User applicant)`: create Application entity with status SUBMITTED, save, call `AuditAgent.recordEvent(APPLICATION_SUBMITTED, ...)`, then asynchronously (or synchronously) trigger `DocumentValidationAgent.validate(applicationId)`
    - `getStatusForApplicant(UUID applicationId, User requester)`: enforce ownership — if `application.applicantId ≠ requester.id` throw `UnauthorisedResourceException` (→ 404); return status + last-updated; when DECISION_RECORDED include decision value and timestamp; never include creditScore or fairness details
    - `getReviewForUnderwriter(UUID applicationId)`: return full `ApplicationReviewResponse` for UNDERWRITER/ADMIN
    - `recordDecision(UUID applicationId, UnderwriterDecisionRequest, User underwriter)`: validate application is in AWAITING_UNDERWRITER_REVIEW; persist `UnderwriterDecision` atomically; set status DECISION_RECORDED; call `AuditAgent.recordEvent(FINAL_DECISION_RECORDED, ...)`
    - `listApplicationsForUnderwriter(Pageable)`: return paginated list filtered by AWAITING_UNDERWRITER_REVIEW
    - _Requirements: 2.1, 2.7, 7.1, 7.2, 7.3, 7.5, 7.6, 7.7, 10.1, 10.3, 10.4, 10.5_

  - [ ] 12.2 Implement `DocumentService`
    - `DocumentService.java` in `service/`
    - `storeDocument(MultipartFile, UUID applicationId, DocumentType)`: validate file size ≤ 10 MB, MIME type in {application/pdf, image/jpeg, image/png}, and magic bytes; generate UUID-based storage path `{baseDir}/{UUID}/{sanitisedFilename}`; stream to storage; persist `Document` metadata; throw appropriate exceptions for size/type violations
    - _Requirements: 2.3, 2.4, 2.5, 2.6, 13.2, 13.3_

  - [ ] 12.3 Implement `PolicyThresholdService`
    - `PolicyThresholdService.java` in `service/`
    - `createThreshold(PolicyThresholdRequest, User creator)`: validate approveThreshold > referThreshold (throw 422 if not); mark current ACTIVE record as SUPERSEDED; create and save new ACTIVE record; return created entity
    - `getActiveThreshold()`: return the single ACTIVE record; throw if none found
    - `listAllThresholds()`: return all records ordered by createdAt DESC
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ]* 12.4 Write property test for policy threshold approve > refer validation (Property 16)
    - **Property 16: Policy Threshold Validity — Approve > Refer**
    - Create `PolicyThresholdPropertyTest.java`; `@Property(tries = 500)`; generate pairs (a, r) in [0,1000]; assert createThreshold accepts iff a > r, rejects with HTTP 422 iff a ≤ r
    - **Validates: Requirements 9.2**

  - [ ]* 12.5 Write property test for exactly one active threshold at all times (Property 17)
    - **Property 17: Exactly One Active Policy Threshold at All Times**
    - In `PolicyThresholdPropertyTest.java`; `@Property(tries = 100)`; run sequences of threshold creations; after each creation assert exactly one ACTIVE record exists in the repository
    - **Validates: Requirements 9.3**

  - [ ] 12.6 Implement `UserService`
    - `UserService.java` in `service/`
    - `loadUserByUsername(String)`: implement `UserDetailsService`; wrap in `UserPrincipal`
    - `createUser(String username, String rawPassword, UserRole role)`: encode password with `BCryptPasswordEncoder`; save `User`
    - `listUsers()`: return all users for ADMIN endpoint
    - _Requirements: 1.1, 1.5_


- [ ] 13. REST controllers — API layer
  - [ ] 13.1 Implement `AuthController`
    - `POST /api/v1/auth/login`: authenticate username/password via `AuthenticationManager`; on success call `JwtTokenProvider.generateToken()`; return `AuthResponse { token }`; on failure return 401
    - _Requirements: 1.1_

  - [ ] 13.2 Implement `ApplicationController`
    - `POST /api/v1/applications` (APPLICANT): validate request with `@Valid`; call `ApplicationService.submitApplication()`; return 201 with applicationId
    - `GET /api/v1/applications/{id}/status` (APPLICANT): call `ApplicationService.getStatusForApplicant()`; return `ApplicationStatusResponse`; ownership enforced in service (→ 404 on violation)
    - `GET /api/v1/applications` (UNDERWRITER, ADMIN): paginated list via `ApplicationService.listApplicationsForUnderwriter()`
    - `GET /api/v1/applications/{id}/review` (UNDERWRITER, ADMIN): call `ApplicationService.getReviewForUnderwriter()`; include fairness flag in response per requirements 6.8
    - _Requirements: 2.1, 2.2, 6.8, 7.1, 7.2, 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 13.3 Write property test for valid application submission (Property 3)
    - **Property 3: Valid Application Submission Creates SUBMITTED Record**
    - Create `ApplicationSubmissionPropertyTest.java`; `@Property(tries = 100)`; generate valid form payloads (all fields non-null, amounts > 0); POST to `/api/v1/applications`; assert 201, non-null applicationId, and persisted Application has status SUBMITTED
    - **Validates: Requirements 2.1**

  - [ ]* 13.4 Write property test for missing fields returns 422 (Property 4)
    - **Property 4: Missing Required Fields Returns 422**
    - In `ApplicationSubmissionPropertyTest.java`; generate non-empty subsets of the 5 required fields to omit; POST incomplete payloads; assert 422 with field-level errors identifying each missing field
    - **Validates: Requirements 2.2**

  - [ ] 13.5 Implement `DocumentController`
    - `POST /api/v1/applications/{id}/documents` (APPLICANT): accept multipart file + document type; call `DocumentService.storeDocument()`; on success return 201 with Document metadata; `@ControllerAdvice` handles 413 / 415 mapping
    - _Requirements: 2.3, 2.4, 2.5, 2.6_

  - [ ] 13.6 Implement `DecisionController`
    - `POST /api/v1/applications/{id}/decision` (UNDERWRITER): validate `@Valid UnderwriterDecisionRequest` (justificationText ≥ 20 chars enforced by Bean Validation + GlobalExceptionHandler); call `ApplicationService.recordDecision()`; return 200; prevent modification if already in DECISION_RECORDED status
    - _Requirements: 7.3, 7.4, 7.5, 7.6, 7.7_

  - [ ]* 13.7 Write property test for decision justification length guard (Property 13)
    - **Property 13: Decision Accepted Iff Justification Length ≥ 20**
    - Create `DecisionControllerPropertyTest.java`; `@Property(tries = 100)`; generate strings of varying length; POST to decision endpoint; assert accepted (200) iff length ≥ 20, rejected (422) iff length < 20
    - **Validates: Requirements 7.3, 7.4**

  - [ ]* 13.8 Write property test for decided application immutability (Property 14)
    - **Property 14: Decided Application Is Immutable**
    - Create `ApplicationImmutabilityPropertyTest.java`; set application to DECISION_RECORDED; attempt modify operations on record, decision, creditScore, recommendation; assert each attempt is rejected
    - **Validates: Requirements 7.7**

  - [ ] 13.9 Implement `PolicyController`
    - `GET /api/v1/policies` (UNDERWRITER, ADMIN): call `PolicyThresholdService.listAllThresholds()`; return ordered list with ACTIVE indicator
    - `POST /api/v1/policies` (ADMIN): call `PolicyThresholdService.createThreshold()`; return 201 with new record
    - _Requirements: 9.1, 9.2, 9.3, 9.6_

  - [ ] 13.10 Implement `AuditController`
    - `GET /api/v1/audit/{applicationId}` (UNDERWRITER, ADMIN): return all audit events for application ordered by `created_at` ASC
    - `GET /api/v1/audit/export` (ADMIN): accept `from` and `to` ISO-8601 date params; return JSON document of all events in range
    - _Requirements: 8.3, 8.4_

  - [ ] 13.11 Implement `AdminController` (user management)
    - `GET /api/v1/admin/users` (ADMIN): call `UserService.listUsers()`
    - `POST /api/v1/admin/users` (ADMIN): call `UserService.createUser()`; return 201
    - _Requirements: 1.6_


- [ ] 14. Backend security and access-control property tests
  - [ ]* 14.1 Write property test for applicant cannot access other applicants' applications (Property 18)
    - **Property 18: Applicant Cannot Access Other Applicants' Applications**
    - Create `ApplicationAccessPropertyTest.java`; `@Property(tries = 100)`; generate user U1 and application belonging to U2; authenticate as U1 and GET status of U2's application; assert HTTP 404 with no application data in response body
    - **Validates: Requirements 10.3**

  - [ ]* 14.2 Write property test for applicant response contains no restricted fields (Property 19)
    - **Property 19: APPLICANT Response Contains No Restricted Fields**
    - In `ApplicationAccessPropertyTest.java`; `@Property(tries = 100)`; fetch status response as APPLICANT; assert response body contains none of: creditScore, dtiSubScore, incomeStabilityScore, creditHistoryScore, fairnessResult, fairnessDelta, auditEvents, underwriterJustification
    - **Validates: Requirements 10.5**

- [ ] 15. Checkpoint — run full backend test suite
  - Run `mvn test` (or `mvn verify` with Testcontainers); all unit, property, and integration tests must pass. Ask the user if any issues arise.


- [ ] 16. Frontend foundation — install dependencies and configure tooling
  - [ ] 16.1 Install frontend dependencies
    - Install production packages: `axios`, `react-router-dom`
    - Install dev/test packages: `vitest`, `@vitest/coverage-v8`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `fast-check`, `jsdom`
    - Install Tailwind CSS: `tailwindcss`, `@tailwindcss/vite` (or `postcss` + `autoprefixer` for v3); configure `tailwind.config.js` and update `index.css` with `@tailwind` directives
    - Update `vite.config.ts` to add Vitest configuration (`environment: 'jsdom'`, `setupFiles`)
    - _Requirements: 11.1_

  - [ ] 16.2 Set up shared frontend infrastructure
    - Create `src/contexts/AuthContext.tsx`: store JWT and decoded role in React state (not localStorage/sessionStorage); expose `login(token)`, `logout()`, `role`, `token`, `isAuthenticated`; on token expiry redirect to `/login` and clear token
    - Create `src/contexts/ApiContext.tsx`: Axios instance with `baseURL: /api/v1`; request interceptor injects `Authorization: Bearer <token>` from `AuthContext`; response interceptor catches 401 and triggers logout/redirect
    - Create `src/services/authService.ts`, `applicationService.ts`, `documentService.ts`, `auditService.ts` as thin Axios wrappers for each API endpoint group
    - _Requirements: 11.1, 11.2_

  - [ ] 16.3 Implement `ProtectedRoute` and `App.tsx` routing
    - `src/components/common/ProtectedRoute.tsx`: check `isAuthenticated` and `role`; redirect unauthenticated users to `/login`; redirect users accessing wrong-role routes to their dashboard
    - `App.tsx`: configure `BrowserRouter` with routes: `/login`, `/applicant/dashboard`, `/applicant/apply`, `/applicant/status/:id`, `/underwriter/worklist`, `/underwriter/review/:id`; wrap role-specific routes with `ProtectedRoute`
    - `src/components/common/Navbar.tsx`: show username and logout button; role-appropriate navigation links; keyboard navigable with ARIA labels
    - `src/components/common/LoadingSpinner.tsx` and `src/components/common/ErrorMessage.tsx`: `ErrorMessage` maps error codes to user-readable messages per design; no raw HTTP codes or stack traces exposed
    - _Requirements: 11.2, 11.9, 11.10_


- [ ] 17. Frontend pages and components — authentication and applicant flow
  - [ ] 17.1 Implement `LoginPage.tsx`
    - Form with username and password fields, submit button; on submit call `authService.login()`; on success store JWT via `AuthContext.login()`; redirect to role-appropriate dashboard; display user-readable error via `ErrorMessage` on failure
    - All form fields and buttons must have ARIA labels; keyboard navigable (Tab + Enter); sufficient colour contrast
    - _Requirements: 11.1, 11.9, 11.10_

  - [ ] 17.2 Implement `ApplicationForm.tsx` and `SubmitApplicationPage.tsx`
    - `ApplicationForm.tsx`: controlled inputs for requestedAmount, loanPurpose, employmentStatus (select), grossMonthlyIncome, totalMonthlyDebt; client-side required-field validation; disable submit while in-flight; `aria-required`, `aria-invalid`, `aria-describedby` on each field
    - `SubmitApplicationPage.tsx`: render `ApplicationForm`; on successful submission show applicationId and redirect to status page
    - _Requirements: 11.3, 11.9_

  - [ ] 17.3 Implement `DocumentUpload.tsx`
    - File inputs for each document type (GOVERNMENT_ID, INCOME_PROOF, BANK_STATEMENT); before calling API: check `file.size > 10_485_760` and `file.type not in {application/pdf, image/jpeg, image/png}`; display inline error and prevent form submission if either check fails; ARIA labels on all inputs and error messages
    - _Requirements: 11.4, 11.9_

  - [ ]* 17.4 Write property test for frontend file upload client-side validation (Property 22)
    - **Property 22: Frontend File Upload Client-Side Validation**
    - Create `src/components/applicant/DocumentUpload.test.tsx`; use `fast-check` with Vitest; generate file objects with arbitrary size and MIME type; assert form submission is prevented (no API call) for size > 10 MB or invalid MIME; assert allowed for valid inputs
    - **Validates: Requirements 11.4**

  - [ ] 17.5 Implement `ApplicationStatusPage.tsx` and `DashboardPage.tsx`
    - `ApplicationStatusPage.tsx`: fetch status via `applicationService.getStatus(id)`; display current status and last-updated; when DECISION_RECORDED display decision value and timestamp; no score or fairness details shown
    - `DashboardPage.tsx`: show list of applicant's own applications with links to status page
    - _Requirements: 11.5_


- [ ] 18. Frontend pages and components — underwriter flow
  - [ ] 18.1 Implement `WorklistPage.tsx`
    - Fetch paginated applications in AWAITING_UNDERWRITER_REVIEW status; display table with applicationId, applicant reference, requested amount, and link to review; pagination controls (prev/next with ARIA); empty-state message
    - _Requirements: 11.6_

  - [ ] 18.2 Implement `CreditScoreCard.tsx`, `RecommendationBadge.tsx`, and `FairnessResultPanel.tsx`
    - `CreditScoreCard.tsx`: display overall Credit Score and three sub-scores (DTI, Income Stability, Credit History) each with its weight and contribution; accessible table or definition list structure
    - `RecommendationBadge.tsx`: visual badge for APPROVE / REFER / DECLINE with distinct accessible colours; display policy threshold citation
    - `FairnessResultPanel.tsx`: display FAIRNESS_PASSED or FAIRNESS_FLAG; when flag present display flag reason in a highlighted alert; `role="alert"` for screen readers
    - _Requirements: 11.7_

  - [ ] 18.3 Implement `DecisionForm.tsx`
    - Select input for decision value (APPROVED, DECLINED, REFERRED_FOR_FURTHER_REVIEW); textarea for justification (character count displayed; submit button enabled only when length ≥ 20); optional override reason field shown when decision differs from system recommendation; ARIA labels on all fields
    - _Requirements: 11.8, 11.9_

  - [ ]* 18.4 Write property test for frontend decision form submission guard (Property 23)
    - **Property 23: Frontend Decision Form Submission Guard**
    - Create `src/components/underwriter/DecisionForm.test.tsx`; use `fast-check` with Vitest; generate strings of arbitrary length; render `DecisionForm` and type string into justification field; assert submit button disabled when length < 20, enabled when length ≥ 20
    - **Validates: Requirements 11.8**

  - [ ] 18.5 Implement `ReviewPage.tsx`
    - Fetch full review payload via `applicationService.getReview(id)`; compose `CreditScoreCard`, `RecommendationBadge`, `FairnessResultPanel`, document metadata list, and `DecisionForm`; display application form data read-only; surface `FAIRNESS_FLAG` banner when present
    - On decision submission call `applicationService.submitDecision()`; show success state and redirect to worklist on 200; show `ErrorMessage` on error
    - _Requirements: 11.7, 11.8, 11.10_

- [ ] 19. Checkpoint — run full frontend test suite
  - Run `npx vitest --run`; all unit and property tests must pass; no TypeScript compilation errors (`tsc --noEmit`). Ask the user if any issues arise.


- [ ] 20. Cross-cutting — backend integration tests
  - [ ] 20.1 Write full agent pipeline integration test
    - Use `@SpringBootTest` + Testcontainers PostgreSQL; create test applicant user; POST application; POST three documents; wait for agent pipeline to complete; assert Application status = AWAITING_UNDERWRITER_REVIEW; assert all six audit event types present in `audit_events` table; assert CreditScore, Recommendation, and FairnessResult records exist
    - _Requirements: 3.7, 4.8, 5.7, 6.7, 8.6_

  - [ ] 20.2 Write JWT authentication flow integration test
    - Test login with valid credentials returns 200 + JWT; test login with wrong password returns 401; test access to protected endpoint without token returns 401; test access with expired token returns 401
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ] 20.3 Write multipart document upload integration test
    - Upload file > 10 MB → assert 413; upload unsupported MIME type → assert 415; upload valid PDF within limit → assert 201 + Document metadata; assert storage path is UUID-based (non-predictable)
    - _Requirements: 2.3, 2.4, 2.5, 2.6, 13.2_

  - [ ] 20.4 Write audit trail completeness integration test
    - Run full lifecycle (submit → upload → pipeline → underwriter decision); assert all six mandatory audit event types are present, ordered by created_at ASC, and contain required fields per requirements 8.1; assert `GET /api/v1/audit/{applicationId}` returns them in correct order
    - _Requirements: 8.1, 8.3, 8.6_

- [ ] 21. Final checkpoint — full system validation
  - Run `mvn verify` (includes all unit, property, and integration tests); run `npx vitest --run` for frontend; verify `mvn compile` and `tsc --noEmit` produce zero errors; confirm Flyway migrations apply cleanly on a fresh schema. Ask the user if any issues arise.


---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP, but are strongly recommended for a production-quality system
- Each task references specific requirements for full traceability back to the requirements document
- Property-based tests cover all 23 correctness properties defined in the design document
- Checkpoints (tasks 5, 11, 15, 19, 21) act as integration gates — resolve issues before proceeding
- Flyway (tasks 1.2–1.6) replaces `ddl-auto: update`; remove that setting before running the application for the first time
- The `DocumentParser` interface in task 7.3 is intentionally a stub — the full OCR/PDF-parsing implementation is a future enhancement; the agent pipeline and all tests work with the stub
- Frontend JWT is stored in React state (in-memory) per requirements 11.1 — this means the token is lost on page refresh, which is intentional per the security design
- All 23 property tests are annotated with `@Tag("Feature: loan-credit-processing-agent, Property N: ...")` on the backend (jqwik) and grouped in dedicated `*.test.tsx` files on the frontend (fast-check + Vitest)


## Task Dependency Graph

```json
{
  "waves": [
    {
      "id": 0,
      "tasks": ["1.1", "1.2"]
    },
    {
      "id": 1,
      "tasks": ["1.3", "1.4", "1.5", "1.6"]
    },
    {
      "id": 2,
      "tasks": ["2.1"]
    },
    {
      "id": 3,
      "tasks": ["2.2", "2.3", "2.4"]
    },
    {
      "id": 4,
      "tasks": ["2.5", "3.1", "4.3", "4.4"]
    },
    {
      "id": 5,
      "tasks": ["3.3", "4.1", "4.2", "12.6"]
    },
    {
      "id": 6,
      "tasks": ["3.2", "3.4", "6.1", "16.1"]
    },
    {
      "id": 7,
      "tasks": ["3.5", "3.6", "6.2", "8.1", "16.2"]
    },
    {
      "id": 8,
      "tasks": ["3.7", "7.1", "8.2", "16.3"]
    },
    {
      "id": 9,
      "tasks": ["7.2", "8.3"]
    },
    {
      "id": 10,
      "tasks": ["7.3", "8.4", "8.5", "17.1"]
    },
    {
      "id": 11,
      "tasks": ["7.4", "8.6", "9.1", "17.2", "17.3"]
    },
    {
      "id": 12,
      "tasks": ["9.2", "9.3", "10.1", "17.4", "17.5"]
    },
    {
      "id": 13,
      "tasks": ["10.2", "12.1", "12.3", "18.1"]
    },
    {
      "id": 14,
      "tasks": ["10.3", "10.4", "10.5", "12.2", "12.4", "12.5", "18.2", "18.3"]
    },
    {
      "id": 15,
      "tasks": ["13.1", "13.2", "13.5", "13.9", "13.10", "13.11", "18.4"]
    },
    {
      "id": 16,
      "tasks": ["13.3", "13.4", "13.6", "13.7", "13.8", "18.5"]
    },
    {
      "id": 17,
      "tasks": ["14.1", "14.2"]
    },
    {
      "id": 18,
      "tasks": ["20.1", "20.2", "20.3"]
    },
    {
      "id": 19,
      "tasks": ["20.4"]
    }
  ]
}
```
