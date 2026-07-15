-- V3__create_scoring_tables.sql
-- Creates the four scoring / decision tables: credit_scores, recommendations,
-- fairness_results, and underwriter_decisions.

-- -----------------------------------------------------------------------
-- credit_scores
-- -----------------------------------------------------------------------
CREATE TABLE credit_scores (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          UUID          NOT NULL UNIQUE REFERENCES applications(id),
    credit_score            DECIMAL(7,2)  NOT NULL,
    dti_ratio               DECIMAL(5,2)  NOT NULL,
    dti_sub_score           INTEGER       NOT NULL,
    income_stability_score  INTEGER       NOT NULL,
    credit_history_score    INTEGER       NOT NULL,
    dti_weight              DECIMAL(4,2)  NOT NULL,
    income_stability_weight DECIMAL(4,2)  NOT NULL,
    credit_history_weight   DECIMAL(4,2)  NOT NULL,
    policy_threshold_id     UUID          NOT NULL REFERENCES policy_thresholds(id),
    computed_at             TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------
-- recommendations
-- -----------------------------------------------------------------------
CREATE TABLE recommendations (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id        UUID        NOT NULL UNIQUE REFERENCES applications(id),
    recommendation_value  VARCHAR(30) NOT NULL,
    policy_threshold_id   UUID        NOT NULL REFERENCES policy_thresholds(id),
    explanation           TEXT        NOT NULL,
    produced_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------
-- fairness_results
-- -----------------------------------------------------------------------
CREATE TABLE fairness_results (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id         UUID         NOT NULL UNIQUE REFERENCES applications(id),
    original_credit_score  DECIMAL(7,2) NOT NULL,
    anonymised_credit_score DECIMAL(7,2) NOT NULL,
    fairness_delta         DECIMAL(7,2) NOT NULL,
    fairness_outcome       VARCHAR(30)  NOT NULL,
    flag_reason            TEXT,
    evaluated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------
-- underwriter_decisions
-- -----------------------------------------------------------------------
CREATE TABLE underwriter_decisions (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id        UUID        NOT NULL UNIQUE REFERENCES applications(id),
    underwriter_id        BIGINT      NOT NULL REFERENCES users(id),
    decision_value        VARCHAR(30) NOT NULL,
    justification_text    TEXT        NOT NULL,
    override_reason       TEXT,
    system_recommendation VARCHAR(30) NOT NULL,
    decided_at            TIMESTAMP   NOT NULL DEFAULT NOW()
);
