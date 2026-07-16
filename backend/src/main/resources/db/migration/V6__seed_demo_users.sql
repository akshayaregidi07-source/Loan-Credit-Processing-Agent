-- V6__seed_demo_users.sql
-- Inserts three interactive demo users for development and testing.
-- All passwords are BCrypt-encoded (cost 10), generated at migration authoring time.
--
--   admin        / admin123
--   underwriter  / underwriter123
--   applicant    / applicant123
--
-- Uses WHERE NOT EXISTS so the migration is idempotent: existing rows
-- are never modified regardless of how many times it is applied.

-- ADMIN — username: admin, password: admin123
INSERT INTO users (username, password, email, role, created_at, enabled)
SELECT 'admin',
       '$2a$10$wk3KFOiDqm5G.R3WsTvpRectG/QX/iX1AIPyM5v2mS0ERPdapVpiO',
       'admin@techvest.ai',
       'ADMIN',
       NOW(),
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- UNDERWRITER — username: underwriter, password: underwriter123
INSERT INTO users (username, password, email, role, created_at, enabled)
SELECT 'underwriter',
       '$2a$10$SKYGXtjfVpB3Kcc4pBSLXePd/l8uHU9qeYVPvdd4KPd/.s1oe5djK',
       'underwriter@techvest.ai',
       'UNDERWRITER',
       NOW(),
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'underwriter');

-- APPLICANT — username: applicant, password: applicant123
INSERT INTO users (username, password, email, role, created_at, enabled)
SELECT 'applicant',
       '$2a$10$3emPOH4Dr.A8AikrKTWlKeDTh.B18hHPFJ67bZdldaMYbtepppwpm',
       'applicant@techvest.ai',
       'APPLICANT',
       NOW(),
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'applicant');
