-- V4__create_audit_events.sql
-- Creates the append-only audit_events table and installs PostgreSQL rules
-- that silently discard any UPDATE or DELETE against it, making the table
-- effectively immutable after insert.

-- -----------------------------------------------------------------------
-- audit_events
-- -----------------------------------------------------------------------
CREATE TABLE audit_events (
    event_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID         NOT NULL REFERENCES applications(id),
    event_type     VARCHAR(60)  NOT NULL,
    event_payload  JSONB        NOT NULL,
    actor          VARCHAR(100) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_application_id ON audit_events(application_id);
CREATE INDEX idx_audit_events_created_at     ON audit_events(created_at);

-- -----------------------------------------------------------------------
-- Immutability rules — UPDATE and DELETE are silently discarded.
-- -----------------------------------------------------------------------
CREATE RULE no_update_audit AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
CREATE RULE no_delete_audit AS ON DELETE TO audit_events DO INSTEAD NOTHING;
