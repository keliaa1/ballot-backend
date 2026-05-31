package com.ballot.event;

public class VoteCastEvent extends BallotAuditEvent {
    public VoteCastEvent(Object source, String userId, String voteId, String ballotId, String ipAddress, String userAgent) {
        super(source, userId, "VOTE_CAST", "VOTE", voteId, ipAddress, userAgent,
              String.format("{\"ballotId\": \"%s\"}", ballotId));
    }
}
