package com.ballot.event;

public class OrgCreatedEvent extends BallotAuditEvent {
    public OrgCreatedEvent(Object source, String userId, String orgId, String ipAddress, String userAgent) {
        super(source, userId, "ORG_CREATED", "ORGANIZATION", orgId, ipAddress, userAgent,
              "{\"action\": \"create\"}");
    }
}
