package com.ballot.service;

import com.ballot.entity.*;
import com.ballot.repository.MembershipRepository;
import com.ballot.repository.VoteRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Dynamic ballot scheduler — schedules tasks at the EXACT instant they should fire,
 * instead of polling every 60 seconds.
 *
 * Features:
 *   1. Activate DRAFT ballot at startDate
 *   2. Close ACTIVE ballot at endDate
 *   3. Send voting reminders to non-voters at (endDate - 24h) and (endDate - 1h)
 *   4. Publish results 2 minutes after close (verification delay)
 *
 * All pending tasks are tracked in metadata maps so they can be exposed via
 * GET /api/scheduler/status for the UI timeline panel.
 */
@Service
@Slf4j
public class DynamicBallotScheduler {

    // ─── Task type constants ──────────────────────────────────────────────────
    public static final String TASK_ACTIVATE    = "ACTIVATE";
    public static final String TASK_CLOSE       = "CLOSE";
    public static final String TASK_REMINDER_24 = "REMINDER_24H";
    public static final String TASK_REMINDER_1  = "REMINDER_1H";
    public static final String TASK_RESULTS     = "RESULTS";

    // ─── Record for UI status exposure ────────────────────────────────────────
    public record ScheduledTaskInfo(
            String key,
            String ballotId,
            String ballotTitle,
            String taskType,
            LocalDateTime scheduledAt
    ) {}

    // ─── Dependencies ─────────────────────────────────────────────────────────
    private final ThreadPoolTaskScheduler taskScheduler;
    private final BallotService ballotService;
    private final ResultService resultService;
    private final NotificationService notificationService;
    private final MembershipRepository membershipRepository;
    private final VoteRepository voteRepository;

    /**
     * @Lazy on BallotService breaks the circular dependency:
     * BallotService → DynamicBallotScheduler → BallotService.
     * Spring injects a CGLIB proxy here; the real bean resolves on first use.
     */
    @Autowired
    public DynamicBallotScheduler(
            ThreadPoolTaskScheduler taskScheduler,
            @Lazy BallotService ballotService,
            ResultService resultService,
            NotificationService notificationService,
            MembershipRepository membershipRepository,
            VoteRepository voteRepository) {
        this.taskScheduler = taskScheduler;
        this.ballotService = ballotService;
        this.resultService = resultService;
        this.notificationService = notificationService;
        this.membershipRepository = membershipRepository;
        this.voteRepository = voteRepository;
    }

    // ─── Internal state ───────────────────────────────────────────────────────
    /** Live futures — used to cancel tasks */
    private final Map<String, ScheduledFuture<?>> pendingFutures  = new ConcurrentHashMap<>();
    /** Metadata — used by the status endpoint + UI */
    private final Map<String, ScheduledTaskInfo>  pendingMetadata = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Public scheduling API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Call this when a ballot is created or updated.
     * Cancels any existing tasks for the ballot and reschedules all four task types.
     */
    public void scheduleAll(Ballot ballot) {
        cancelAllTasksForBallot(ballot.getId());
        scheduleActivation(ballot);
        scheduleClosure(ballot);
        scheduleVotingReminders(ballot);
        scheduleResultsPublication(ballot);
    }

    /** Cancel ALL pending tasks for a ballot (on update / delete / manual close). */
    public void cancelAllTasksForBallot(String ballotId) {
        cancelTask(key(ballotId, TASK_ACTIVATE));
        cancelTask(key(ballotId, TASK_CLOSE));
        cancelTask(key(ballotId, TASK_REMINDER_24));
        cancelTask(key(ballotId, TASK_REMINDER_1));
        cancelTask(key(ballotId, TASK_RESULTS));
    }

    /** Returns all pending tasks — used by SchedulerStatusController. */
    public List<ScheduledTaskInfo> getPendingTasks() {
        return List.copyOf(pendingMetadata.values());
    }

    /** Returns pending task count (for tests / monitoring). */
    public int pendingTaskCount() {
        return pendingFutures.size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Individual schedulers
    // ═══════════════════════════════════════════════════════════════════════════

    public void scheduleActivation(Ballot ballot) {
        if (ballot.getStatus() != BallotStatus.DRAFT) return;
        LocalDateTime startDate = ballot.getStartDate();
        if (startDate == null || startDate.isBefore(LocalDateTime.now())) {
            log.warn("[DYN-SCHED] Skipping activation for ballot {} — startDate is null or in the past", ballot.getId());
            return;
        }
        String k = key(ballot.getId(), TASK_ACTIVATE);
        schedule(k, ballot.getId(), ballot.getTitle(), TASK_ACTIVATE, startDate,
                () -> runActivation(ballot.getId()));
    }

    public void scheduleClosure(Ballot ballot) {
        LocalDateTime endDate = ballot.getEndDate();
        if (endDate == null || endDate.isBefore(LocalDateTime.now())) {
            log.warn("[DYN-SCHED] Skipping closure for ballot {} — endDate is null or in the past", ballot.getId());
            return;
        }
        String k = key(ballot.getId(), TASK_CLOSE);
        schedule(k, ballot.getId(), ballot.getTitle(), TASK_CLOSE, endDate,
                () -> runClosure(ballot.getId()));
    }

    public void scheduleVotingReminders(Ballot ballot) {
        LocalDateTime endDate = ballot.getEndDate();
        if (endDate == null) return;

        // 24h before close
        LocalDateTime remind24 = endDate.minusHours(24);
        if (remind24.isAfter(LocalDateTime.now())) {
            String k = key(ballot.getId(), TASK_REMINDER_24);
            schedule(k, ballot.getId(), ballot.getTitle(), TASK_REMINDER_24, remind24,
                    () -> runReminder(ballot.getId(), 24));
        }

        // 1h before close
        LocalDateTime remind1 = endDate.minusHours(1);
        if (remind1.isAfter(LocalDateTime.now())) {
            String k = key(ballot.getId(), TASK_REMINDER_1);
            schedule(k, ballot.getId(), ballot.getTitle(), TASK_REMINDER_1, remind1,
                    () -> runReminder(ballot.getId(), 1));
        }
    }

    public void scheduleResultsPublication(Ballot ballot) {
        LocalDateTime endDate = ballot.getEndDate();
        if (endDate == null) return;
        // 2 minutes after close for "verification" delay
        LocalDateTime publishAt = endDate.plusMinutes(2);
        if (publishAt.isAfter(LocalDateTime.now())) {
            String k = key(ballot.getId(), TASK_RESULTS);
            schedule(k, ballot.getId(), ballot.getTitle(), TASK_RESULTS, publishAt,
                    () -> runResultsPublication(ballot.getId()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Task runners
    // ═══════════════════════════════════════════════════════════════════════════

    private void runActivation(String ballotId) {
        log.info("[DYN-SCHED] Activating ballot {}", ballotId);
        try {
            Ballot ballot = ballotService.findBallot(ballotId);
            if (ballot.getStatus() != BallotStatus.DRAFT) return;
            ballotService.activateBallot(ballot);
            notifyAllMembers(ballot, (user) ->
                    notificationService.sendBallotStartedNotification(user, ballot));
            log.info("[DYN-SCHED] Ballot {} activated", ballotId);
        } catch (Exception e) {
            log.error("[DYN-SCHED] Error activating ballot {}: {}", ballotId, e.getMessage(), e);
        } finally {
            pendingFutures.remove(key(ballotId, TASK_ACTIVATE));
            pendingMetadata.remove(key(ballotId, TASK_ACTIVATE));
        }
    }

    private void runClosure(String ballotId) {
        log.info("[DYN-SCHED] Closing ballot {}", ballotId);
        try {
            Ballot ballot = ballotService.findBallot(ballotId);
            if (ballot.getStatus() != BallotStatus.ACTIVE) return;
            ballotService.closeBallot(ballot);
            resultService.computeResults(ballot);
            log.info("[DYN-SCHED] Ballot {} closed and results computed", ballotId);
        } catch (Exception e) {
            log.error("[DYN-SCHED] Error closing ballot {}: {}", ballotId, e.getMessage(), e);
        } finally {
            pendingFutures.remove(key(ballotId, TASK_CLOSE));
            pendingMetadata.remove(key(ballotId, TASK_CLOSE));
        }
    }

    private void runReminder(String ballotId, int hoursLeft) {
        log.info("[DYN-SCHED] Sending {}h reminder for ballot {}", hoursLeft, ballotId);
        try {
            Ballot ballot = ballotService.findBallot(ballotId);
            if (ballot.getStatus() != BallotStatus.ACTIVE) return;

            // Only notify members who have NOT yet voted
            membershipRepository
                    .findAllByOrganizationIdAndStatus(ballot.getOrganization().getId(), MembershipStatus.ACTIVE)
                    .stream()
                    .filter(m -> !voteRepository.existsByBallotIdAndUserId(ballotId, m.getUser().getId()))
                    .forEach(m -> notificationService.sendVotingReminderNotification(m.getUser(), ballot, hoursLeft));

            log.info("[DYN-SCHED] {}h reminders sent for ballot {}", hoursLeft, ballotId);
        } catch (Exception e) {
            log.error("[DYN-SCHED] Error sending {}h reminder for ballot {}: {}", hoursLeft, ballotId, e.getMessage(), e);
        } finally {
            String taskType = hoursLeft >= 24 ? TASK_REMINDER_24 : TASK_REMINDER_1;
            pendingFutures.remove(key(ballotId, taskType));
            pendingMetadata.remove(key(ballotId, taskType));
        }
    }

    private void runResultsPublication(String ballotId) {
        log.info("[DYN-SCHED] Publishing results for ballot {}", ballotId);
        try {
            Ballot ballot = ballotService.findBallot(ballotId);
            if (ballot.getStatus() != BallotStatus.CLOSED) {
                log.info("[DYN-SCHED] Ballot {} is not CLOSED yet, skipping results publication", ballotId);
                return;
            }
            // Ensure results are computed (idempotent)
            resultService.computeResults(ballot);
            // Notify all members
            notifyAllMembers(ballot, (user) ->
                    notificationService.sendResultsPublishedNotification(user, ballot));
            log.info("[DYN-SCHED] Results published for ballot {}", ballotId);
        } catch (Exception e) {
            log.error("[DYN-SCHED] Error publishing results for ballot {}: {}", ballotId, e.getMessage(), e);
        } finally {
            pendingFutures.remove(key(ballotId, TASK_RESULTS));
            pendingMetadata.remove(key(ballotId, TASK_RESULTS));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void schedule(String k, String ballotId, String ballotTitle,
                          String taskType, LocalDateTime fireAt, Runnable task) {
        cancelTask(k);
        Instant trigger = fireAt.atZone(ZoneId.systemDefault()).toInstant();
        ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
        pendingFutures.put(k, future);
        pendingMetadata.put(k, new ScheduledTaskInfo(k, ballotId, ballotTitle, taskType, fireAt));
        log.info("[DYN-SCHED] Task '{}' scheduled for {} at {}", taskType, ballotId, fireAt);
    }

    private void cancelTask(String key) {
        ScheduledFuture<?> f = pendingFutures.remove(key);
        pendingMetadata.remove(key);
        if (f != null && !f.isDone()) {
            boolean cancelled = f.cancel(false);
            log.info("[DYN-SCHED] Task '{}' cancelled: {}", key, cancelled);
        }
    }

    private void notifyAllMembers(Ballot ballot, java.util.function.Consumer<User> action) {
        try {
            membershipRepository
                    .findAllByOrganizationIdAndStatus(ballot.getOrganization().getId(), MembershipStatus.ACTIVE)
                    .forEach(m -> action.accept(m.getUser()));
        } catch (Exception e) {
            log.error("[DYN-SCHED] Notification error for ballot {}: {}", ballot.getId(), e.getMessage());
        }
    }

    private static String key(String ballotId, String taskType) {
        return ballotId + ":" + taskType;
    }

    @PreDestroy
    public void shutdown() {
        log.info("[DYN-SCHED] Shutting down — cancelling {} pending task(s)", pendingFutures.size());
        pendingFutures.forEach((k, f) -> { if (!f.isDone()) f.cancel(false); });
        pendingFutures.clear();
        pendingMetadata.clear();
    }
}
