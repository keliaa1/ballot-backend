package com.ballot.event;

public class UserLoginEvent extends BallotAuditEvent {
    public UserLoginEvent(Object source, String userId, String ipAddress, String userAgent) {
        super(source, userId, "USER_LOGIN", "USER", userId, ipAddress, userAgent,
              "{\"action\": \"login\"}");
    }
}
