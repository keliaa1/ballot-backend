package com.ballot.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Base class for all Ballot audit events.
 */
@Getter
public abstract class BallotAuditEvent extends ApplicationEvent {

    private final String userId;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final String ipAddress;
    private final String userAgent;
    private final String details;

    protected BallotAuditEvent(Object source, String userId, String action,
                                String entityType, String entityId,
                                String ipAddress, String userAgent, String details) {
        super(source);
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
    }
}
