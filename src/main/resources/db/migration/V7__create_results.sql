-- V7: Create results table
CREATE TABLE results (
    id          VARCHAR(36)    PRIMARY KEY,
    ballot_id   VARCHAR(36)    NOT NULL REFERENCES ballots(id),
    option_id   VARCHAR(36)    NOT NULL REFERENCES ballot_options(id),
    vote_count  BIGINT         NOT NULL DEFAULT 0,
    percentage  DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    computed_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_result_ballot_option UNIQUE (ballot_id, option_id)
);

CREATE INDEX idx_results_ballot_id ON results(ballot_id);
