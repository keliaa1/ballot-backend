-- V6: Create votes table
CREATE TABLE votes (
    id           VARCHAR(36) PRIMARY KEY,
    ballot_id    VARCHAR(36) NOT NULL REFERENCES ballots(id),
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    option_id    VARCHAR(36) NOT NULL REFERENCES ballot_options(id),
    submitted_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP,
    CONSTRAINT uk_vote_ballot_user UNIQUE (ballot_id, user_id)
);

CREATE INDEX idx_votes_ballot_id ON votes(ballot_id);
CREATE INDEX idx_votes_user_id ON votes(user_id);
CREATE INDEX idx_votes_option_id ON votes(option_id);
