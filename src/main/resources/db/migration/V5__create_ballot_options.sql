-- V5: Create ballot_options table
CREATE TABLE ballot_options (
    id            VARCHAR(36)  PRIMARY KEY,
    ballot_id     VARCHAR(36)  NOT NULL REFERENCES ballots(id) ON DELETE CASCADE,
    option_text   VARCHAR(500) NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_ballot_options_ballot_id ON ballot_options(ballot_id);
