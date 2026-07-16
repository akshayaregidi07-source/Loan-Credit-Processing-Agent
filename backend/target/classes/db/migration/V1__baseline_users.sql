-- V1__baseline_users.sql
-- Creates the users table from scratch on a fresh database.
-- On an already-migrated database this will be skipped by Flyway's checksum.
--
-- If you are running this against an existing database that already has a
-- users table from the old Spring Boot JPA auto-DDL, run the following
-- manually before applying this migration:
--   ALTER TABLE users
--       ADD COLUMN IF NOT EXISTS role        VARCHAR(20)  NOT NULL DEFAULT 'APPLICANT',
--       ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
--       ADD COLUMN IF NOT EXISTS enabled     BOOLEAN      NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    role       VARCHAR(20)  NOT NULL DEFAULT 'APPLICANT',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE
);
