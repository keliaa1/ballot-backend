-- V4: Create ballots table
CREATE TABLE ballots (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    title           VARCHAR(255)  NOT NULL,
    description     VARCHAR(2000),
    is_anonymous    BOOLEAN       NOT NULL DEFAULT FALSE,
    multiple_choice BOOLEAN       NOT NULL DEFAULT FALSE,
    start_date      TIMESTAMP     NOT NULL,
    end_date        TIMESTAMP     NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(36)   NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ballot_dates CHECK (end_date > start_date)
);

CREATE INDEX idx_ballots_org_id ON ballots(organization_id);
CREATE INDEX idx_ballots_status ON ballots(status);
CREATE INDEX idx_ballots_start_date ON ballots(start_date);
CREATE INDEX idx_ballots_end_date ON ballots(end_date);
CREATE INDEX idx_ballots_created_by ON ballots(created_by);
