-- V1__baseline_users.sql
-- Extend the existing `users` table with the columns required by the loan system.
-- Uses ADD COLUMN IF NOT EXISTS so this migration is idempotent and safe to run
-- against a database that already has some of these columns.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role        VARCHAR(20)  NOT NULL DEFAULT 'APPLICANT',
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS enabled     BOOLEAN      NOT NULL DEFAULT TRUE;
