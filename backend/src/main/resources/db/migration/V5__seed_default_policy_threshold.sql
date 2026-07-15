-- V5__seed_default_policy_threshold.sql
-- Seeds a system admin user (if one does not already exist) and inserts the
-- default active policy threshold that the application requires at startup.

-- -----------------------------------------------------------------------
-- System user
-- Bcrypt hash of a placeholder password; this account is not intended for
-- interactive login — the real admin password should be set by operations.
-- -----------------------------------------------------------------------
INSERT INTO users (username, password, email, role, created_at, enabled)
SELECT
    'system',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
    'system@techvest.ai',
    'ADMIN',
    NOW(),
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'system'
);

-- -----------------------------------------------------------------------
-- Default active policy threshold
-- approve_threshold = 700, refer_threshold = 500
-- created_by references the system user inserted above
-- -----------------------------------------------------------------------
INSERT INTO policy_thresholds (approve_threshold, refer_threshold, status, created_at, created_by)
SELECT 700, 500, 'ACTIVE', NOW(), id
FROM users
WHERE username = 'system'
LIMIT 1;
