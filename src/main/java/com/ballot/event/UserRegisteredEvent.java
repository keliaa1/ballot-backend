package com.ballot.event;

public class UserRegisteredEvent extends BallotAuditEvent {
    public UserRegisteredEvent(Object source, String userId, String ipAddress, String userAgent) {
        super(source, userId, "USER_REGISTERED", "USER", userId, ipAddress, userAgent,
              "{\"action\": \"registration\"}");
    }
}
