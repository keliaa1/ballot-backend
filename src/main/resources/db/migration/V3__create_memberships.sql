-- V3: Create memberships table
CREATE TABLE memberships (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id),
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id),
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    joined_at       TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_membership_user_org UNIQUE (user_id, organization_id)
);

CREATE INDEX idx_memberships_user_id ON memberships(user_id);
CREATE INDEX idx_memberships_org_id ON memberships(organization_id);
CREATE INDEX idx_memberships_status ON memberships(status);
CREATE INDEX idx_memberships_role ON memberships(role);
