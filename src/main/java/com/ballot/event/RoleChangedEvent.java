package com.ballot.event;

public class RoleChangedEvent extends BallotAuditEvent {
    public RoleChangedEvent(Object source, String actorUserId, String targetUserId,
                            String orgId, String newRole, String ipAddress, String userAgent) {
        super(source, actorUserId, "ROLE_CHANGED", "MEMBERSHIP", targetUserId,
              ipAddress, userAgent,
              String.format("{\"targetUserId\": \"%s\", \"orgId\": \"%s\", \"newRole\": \"%s\"}",
                      targetUserId, orgId, newRole));
    }
}
