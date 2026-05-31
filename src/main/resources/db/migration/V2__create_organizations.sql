-- V2: Create organizations table
CREATE TABLE organizations (
    id            VARCHAR(36)   PRIMARY KEY,
    name          VARCHAR(150)  NOT NULL UNIQUE,
    description   VARCHAR(1000),
    logo_url      TEXT,
    logo_public_id TEXT,
    invite_code   VARCHAR(20)   UNIQUE,
    join_type     VARCHAR(30)   NOT NULL DEFAULT 'PUBLIC',
    created_by    VARCHAR(36)   NOT NULL REFERENCES users(id),
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizations_name ON organizations(name);
CREATE INDEX idx_organizations_status ON organizations(status);
CREATE INDEX idx_organizations_invite_code ON organizations(invite_code);
CREATE INDEX idx_organizations_created_by ON organizations(created_by);
