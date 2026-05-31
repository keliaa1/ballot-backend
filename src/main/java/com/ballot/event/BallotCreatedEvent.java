package com.ballot.event;

public class BallotCreatedEvent extends BallotAuditEvent {
    public BallotCreatedEvent(Object source, String userId, String ballotId, String orgId, String ipAddress, String userAgent) {
        super(source, userId, "BALLOT_CREATED", "BALLOT", ballotId, ipAddress, userAgent,
              String.format("{\"organizationId\": \"%s\"}", orgId));
    }
}
