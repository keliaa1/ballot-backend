package com.ballot.audit;

import com.ballot.entity.AuditLog;
import com.ballot.event.BallotAuditEvent;
import com.ballot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to all BallotAuditEvent subclasses and persists them asynchronously.
 * Using @Async so audit writes never block the main request thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onAuditEvent(BallotAuditEvent event) {
        try {
            AuditLog log = AuditLog.builder()
                    .userId(event.getUserId())
                    .action(event.getAction())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .details(event.getDetails())
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            AuditEventListener.log.error("Failed to persist audit log for action {}: {}", event.getAction(), e.getMessage());
        }
    }
}
