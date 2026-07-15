-- V2__create_core_tables.sql
-- Creates the four core domain tables: applications, documents,
-- policy_thresholds, and document_extraction_payloads.
-- Requires: pgcrypto extension for gen_random_uuid().

-- Enable pgcrypto for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------
-- applications
-- -----------------------------------------------------------------------
CREATE TABLE applications (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id         BIGINT         NOT NULL REFERENCES users(id),
    requested_amount     DECIMAL(15,2)  NOT NULL,
    loan_purpose         VARCHAR(200)   NOT NULL,
    employment_status    VARCHAR(50)    NOT NULL,
    gross_monthly_income DECIMAL(15,2)  NOT NULL,
    total_monthly_debt   DECIMAL(15,2)  NOT NULL,
    status               VARCHAR(50)    NOT NULL DEFAULT 'SUBMITTED',
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_applications_applicant_id ON applications(applicant_id);
CREATE INDEX idx_applications_status       ON applications(status);

-- -----------------------------------------------------------------------
-- documents
-- -----------------------------------------------------------------------
CREATE TABLE documents (
    id                       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id           UUID           NOT NULL REFERENCES applications(id),
    document_type            VARCHAR(50)    NOT NULL,
    original_filename        VARCHAR(255)   NOT NULL,
    mime_type                VARCHAR(100)   NOT NULL,
    file_size_bytes          BIGINT         NOT NULL,
    storage_path             VARCHAR(512)   NOT NULL,
    uploaded_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    validation_status        VARCHAR(50),
    validation_failure_reason TEXT
);

CREATE INDEX idx_documents_application_id ON documents(application_id);

-- -----------------------------------------------------------------------
-- policy_thresholds
-- -----------------------------------------------------------------------
CREATE TABLE policy_thresholds (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    approve_threshold  INTEGER     NOT NULL,
    refer_threshold    INTEGER     NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by         BIGINT      NOT NULL REFERENCES users(id)
);

-- -----------------------------------------------------------------------
-- document_extraction_payloads
-- -----------------------------------------------------------------------
CREATE TABLE document_extraction_payloads (
    id                UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    UUID      NOT NULL UNIQUE REFERENCES applications(id),
    extracted_fields  JSONB     NOT NULL,
    extraction_status VARCHAR(50) NOT NULL,
    extracted_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
