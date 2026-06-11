package com.ballot.service;

import com.ballot.entity.Ballot;
import com.ballot.entity.MembershipStatus;
import com.ballot.repository.MembershipRepository;
import com.ballot.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled jobs for the ballot lifecycle:
 * 1. Activate DRAFT ballots whose startDate has passed.        [@Scheduled fixedDelay]
 * 2. Close ACTIVE ballots whose endDate has passed.            [@Scheduled fixedDelay]
 * 3. Compute results for newly closed ballots.
 * 4. Notify members when ballots start or end.
 * 5. Cleanup expired tokens.                                   [@Scheduled cron — daily 2 AM]
 * 6. Purge old READ notifications older than 90 days.          [@Scheduled cron — daily 3 AM]
 * 7. Log scheduler heartbeat every hour.                       [@Scheduled fixedRate + initialDelay]
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class BallotSchedulerService {

    private final BallotService ballotService;
    private final ResultService resultService;
    private final NotificationService notificationService;
    private final MembershipRepository membershipRepository;
    private final TokenService tokenService;
    private final NotificationRepository notificationRepository;

    // ─── Ballot Lifecycle Jobs (fixedDelay) ───────────────────────────────────

    /**
     * SORTING concept: findDraftBallotsToActivate() uses a JPQL query that retrieves
     * ballots ordered by startDate via the repository layer.
     *
     * fixedDelay = waits N ms AFTER the previous execution finishes before starting next.
     * Runs every 60 seconds to activate DRAFT ballots whose startDate has passed.
     */
    @Scheduled(fixedDelay = 60_000)
    public void activatePendingBallots() {
        List<Ballot> drafts = ballotService.findDraftBallotsToActivate();
        if (drafts.isEmpty()) return;
        log.info("[SCHEDULER] Activating {} draft ballot(s)", drafts.size());

        for (Ballot ballot : drafts) {
            try {
                ballotService.activateBallot(ballot);
                notifyOrganizationMembers(ballot, false);
                log.info("[SCHEDULER] Ballot activated: {}", ballot.getId());
            } catch (Exception e) {
                log.error("[SCHEDULER] Failed to activate ballot {}: {}", ballot.getId(), e.getMessage());
            }
        }
    }

    /**
     * Runs every 60 seconds to close ACTIVE ballots whose endDate has passed
     * and compute their results.
     */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredBallots() {
        List<Ballot> actives = ballotService.findActiveBallotsToClose();
        if (actives.isEmpty()) return;
        log.info("[SCHEDULER] Closing {} active ballot(s)", actives.size());

        for (Ballot ballot : actives) {
            try {
                ballotService.closeBallot(ballot);
                resultService.computeResults(ballot);
                notifyOrganizationMembers(ballot, true);
                log.info("[SCHEDULER] Ballot closed and results computed: {}", ballot.getId());
            } catch (Exception e) {
                log.error("[SCHEDULER] Failed to close ballot {}: {}", ballot.getId(), e.getMessage());
            }
        }
    }

    // ─── Maintenance Jobs (cron expressions) ──────────────────────────────────

    /**
     * SCHEDULING (cron) — Clean up expired JWT / refresh tokens daily at 2 AM.
     * Cron format: second  minute  hour  day-of-month  month  day-of-week
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTokens() {
        int deleted = tokenService.cleanupExpiredTokens();
        log.info("[SCHEDULER] Cleaned up {} expired token(s)", deleted);
    }

    /**
     * SCHEDULING (cron) — Purge READ notifications older than 90 days, daily at 3 AM.
     *
     * Why this complements PAGINATION:
     *   The notifications list endpoint uses PageUtil.of(page, size, sortBy, direction)
     *   so the total result set can grow indefinitely. This job keeps the table lean by
     *   removing stale READ records that would never be surfaced by normal pagination.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldReadNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = notificationRepository.deleteOldReadNotifications(cutoff);
        log.info("[SCHEDULER] Purged {} READ notification(s) older than 90 days", deleted);
    }

    // ─── Health / Observability Job (fixedRate + initialDelay) ────────────────

    /**
     * SCHEDULING (fixedRate + initialDelay) — Log a scheduler health heartbeat every hour.
     *
     * initialDelay = 60_000 ms: waits 60 s after application startup before the FIRST
     *   execution, giving Spring context time to fully initialize.
     *
     * fixedRate = 3_600_000 ms: fires every 1 hour measured from START of the previous
     *   execution (unlike fixedDelay which measures from END).
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void logSchedulerHealth() {
        log.info("[SCHEDULER] Heartbeat OK — all scheduled jobs are running. Timestamp: {}", LocalDateTime.now());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void notifyOrganizationMembers(Ballot ballot, boolean ended) {
        try {
            membershipRepository
                    .findAllByOrganizationIdAndStatus(ballot.getOrganization().getId(), MembershipStatus.ACTIVE)
                    .forEach(membership -> {
                        if (ended) {
                            notificationService.sendBallotEndedNotification(membership.getUser(), ballot);
                        } else {
                            notificationService.sendBallotStartedNotification(membership.getUser(), ballot);
                        }
                    });
        } catch (Exception e) {
            log.error("[SCHEDULER] Failed to send notifications for ballot {}: {}", ballot.getId(), e.getMessage());
        }
    }
}
