package com.ballot.service;

import com.ballot.entity.*;
import com.ballot.repository.MembershipRepository;
import com.ballot.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicBallotSchedulerTest {

    @Mock private ThreadPoolTaskScheduler taskScheduler;
    @Mock private BallotService ballotService;
    @Mock private ResultService resultService;
    @Mock private NotificationService notificationService;
    @Mock private MembershipRepository membershipRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private ScheduledFuture<?> mockFuture;

    @InjectMocks
    private DynamicBallotScheduler dynamicScheduler;

    private Organization org;
    private Ballot draftBallot;
    private Ballot activeBallot;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org = Organization.builder().id("org-1").name("Test Org").build();

        draftBallot = Ballot.builder()
                .id("ballot-draft")
                .organization(org)
                .title("Draft Ballot")
                .status(BallotStatus.DRAFT)
                .startDate(LocalDateTime.now().plusMinutes(30))
                .endDate(LocalDateTime.now().plusHours(25)) // 25h away → triggers 24h reminder
                .build();

        activeBallot = Ballot.builder()
                .id("ballot-active")
                .organization(org)
                .title("Active Ballot")
                .status(BallotStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(2))
                .build();

        // lenient: most tests use this stub but a few (skip/no-op tests) don't call schedule() at all.
        lenient().when(taskScheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
                .thenAnswer(inv -> mockFuture);
    }

    // ── scheduleAll ───────────────────────────────────────────────────────────

    @Test
    void scheduleAll_shouldRegisterMultipleTasksForDraftBallot() {
        dynamicScheduler.scheduleAll(draftBallot);

        // ACTIVATE + CLOSE + REMINDER_24H + RESULTS (1h reminder is < 24h, and endDate is 25h away so also valid)
        // At minimum ACTIVATE, CLOSE, RESULTS should be registered
        assertThat(dynamicScheduler.pendingTaskCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void scheduleAll_shouldCancelExistingTasksBeforeRescheduling() {
        when(mockFuture.isDone()).thenReturn(false);

        dynamicScheduler.scheduleAll(draftBallot); // first schedule
        int firstCount = dynamicScheduler.pendingTaskCount();

        dynamicScheduler.scheduleAll(draftBallot); // reschedule → cancel first, schedule fresh

        // Task count should stay the same (cancelled + re-added)
        assertThat(dynamicScheduler.pendingTaskCount()).isEqualTo(firstCount);
    }

    // ── scheduleActivation ────────────────────────────────────────────────────

    @Test
    void scheduleActivation_shouldRegisterTaskForDraftBallot() {
        dynamicScheduler.scheduleActivation(draftBallot);

        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        assertThat(dynamicScheduler.pendingTaskCount()).isEqualTo(1);
    }

    @Test
    void scheduleActivation_shouldSkipWhenBallotIsNotDraft() {
        dynamicScheduler.scheduleActivation(activeBallot); // ACTIVE, not DRAFT

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
        assertThat(dynamicScheduler.pendingTaskCount()).isZero();
    }

    @Test
    void scheduleActivation_shouldSkipWhenStartDateIsInThePast() {
        draftBallot.setStartDate(LocalDateTime.now().minusMinutes(5));
        dynamicScheduler.scheduleActivation(draftBallot);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    // ── scheduleClosure ───────────────────────────────────────────────────────

    @Test
    void scheduleClosure_shouldRegisterTaskForBallotWithFutureEndDate() {
        dynamicScheduler.scheduleClosure(activeBallot);
        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void scheduleClosure_shouldSkipWhenEndDateIsInThePast() {
        activeBallot.setEndDate(LocalDateTime.now().minusMinutes(1));
        dynamicScheduler.scheduleClosure(activeBallot);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    // ── scheduleVotingReminders ───────────────────────────────────────────────

    @Test
    void scheduleVotingReminders_shouldRegister24hReminderWhenEndDateIsOver25hAway() {
        // draftBallot.endDate = now + 25h → 24h reminder fires in ~1h → valid
        dynamicScheduler.scheduleVotingReminders(draftBallot);
        // Should schedule at least the 24h reminder (endDate - 24h = now + 1h, still future)
        verify(taskScheduler, atLeast(1)).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void scheduleVotingReminders_shouldSkipRemindersWhenEndDateIsTooClose() {
        // endDate = now + 30 min → both reminders are in the past
        Ballot ballotEndingSoon = Ballot.builder()
                .id("ballot-soon").organization(org).title("Soon")
                .status(BallotStatus.ACTIVE)
                .endDate(LocalDateTime.now().plusMinutes(30))
                .build();
        dynamicScheduler.scheduleVotingReminders(ballotEndingSoon);
        // Neither 24h nor 1h reminder should fire (both in the past)
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    // ── cancelAllTasksForBallot ───────────────────────────────────────────────

    @Test
    void cancelAllTasksForBallot_shouldRemoveAllTasks() {
        when(mockFuture.isDone()).thenReturn(false);

        dynamicScheduler.scheduleActivation(draftBallot);
        dynamicScheduler.scheduleClosure(draftBallot);
        assertThat(dynamicScheduler.pendingTaskCount()).isEqualTo(2);

        dynamicScheduler.cancelAllTasksForBallot(draftBallot.getId());
        assertThat(dynamicScheduler.pendingTaskCount()).isZero();
    }

    @Test
    void cancelAllTasksForBallot_shouldNoOpForUnknownBallotId() {
        assertThatNoException()
                .isThrownBy(() -> dynamicScheduler.cancelAllTasksForBallot("no-such-ballot"));
    }

    // ── getPendingTasks ───────────────────────────────────────────────────────

    @Test
    void getPendingTasks_shouldReturnMetadataForScheduledTasks() {
        dynamicScheduler.scheduleActivation(draftBallot);
        dynamicScheduler.scheduleClosure(draftBallot);

        var tasks = dynamicScheduler.getPendingTasks();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(DynamicBallotScheduler.ScheduledTaskInfo::ballotId)
                .containsOnly("ballot-draft");
        assertThat(tasks).extracting(DynamicBallotScheduler.ScheduledTaskInfo::taskType)
                .containsExactlyInAnyOrder("ACTIVATE", "CLOSE");
    }

    // ── Activation task runner (via captured Runnable) ────────────────────────

    @Test
    void activationTask_shouldActivateBallotAndNotifyMembers() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        dynamicScheduler.scheduleActivation(draftBallot);
        verify(taskScheduler).schedule(captor.capture(), any(java.time.Instant.class));

        User member = User.builder().id("u1").email("u@test.com").build();
        Membership membership = Membership.builder().user(member).organization(org).build();

        when(ballotService.findBallot("ballot-draft")).thenReturn(draftBallot);
        when(membershipRepository.findAllByOrganizationIdAndStatus("org-1", MembershipStatus.ACTIVE))
                .thenReturn(java.util.List.of(membership));

        captor.getValue().run();

        verify(ballotService).activateBallot(draftBallot);
        verify(notificationService).sendBallotStartedNotification(member, draftBallot);
    }

    @Test
    void activationTask_shouldSkipWhenBallotIsNoLongerDraft() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        dynamicScheduler.scheduleActivation(draftBallot);
        verify(taskScheduler).schedule(captor.capture(), any(java.time.Instant.class));

        Ballot alreadyActive = Ballot.builder().id("ballot-draft").organization(org)
                .status(BallotStatus.ACTIVE).build();
        when(ballotService.findBallot("ballot-draft")).thenReturn(alreadyActive);

        captor.getValue().run();

        verify(ballotService, never()).activateBallot(any());
    }

    @Test
    void activationTask_shouldHandleExceptionGracefully() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        dynamicScheduler.scheduleActivation(draftBallot);
        verify(taskScheduler).schedule(captor.capture(), any(java.time.Instant.class));

        when(ballotService.findBallot("ballot-draft"))
                .thenThrow(new RuntimeException("DB error"));

        assertThatNoException().isThrownBy(() -> captor.getValue().run());
    }

    // ── Reminder task runner ──────────────────────────────────────────────────

    @Test
    void reminderTask_shouldOnlyNotifyNonVoters() {
        // Schedule 1h reminder manually for activeBallot
        Ballot ballotWith2hEnd = Ballot.builder()
                .id("ballot-2h").organization(org).title("2h Ballot")
                .status(BallotStatus.ACTIVE)
                .endDate(LocalDateTime.now().plusHours(2))
                .build();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        dynamicScheduler.scheduleVotingReminders(ballotWith2hEnd);
        verify(taskScheduler, atLeast(1)).schedule(captor.capture(), any(java.time.Instant.class));

        User voter    = User.builder().id("u-voter").email("voter@t.com").build();
        User nonVoter = User.builder().id("u-non").email("non@t.com").build();
        Membership m1 = Membership.builder().user(voter).organization(org).build();
        Membership m2 = Membership.builder().user(nonVoter).organization(org).build();

        when(ballotService.findBallot("ballot-2h")).thenReturn(ballotWith2hEnd);
        when(membershipRepository.findAllByOrganizationIdAndStatus("org-1", MembershipStatus.ACTIVE))
                .thenReturn(java.util.List.of(m1, m2));
        when(voteRepository.existsByBallotIdAndUserId("ballot-2h", "u-voter")).thenReturn(true);
        when(voteRepository.existsByBallotIdAndUserId("ballot-2h", "u-non")).thenReturn(false);

        captor.getValue().run(); // run the 1h reminder

        // Only non-voter should receive a reminder
        verify(notificationService, never()).sendVotingReminderNotification(eq(voter), any(), anyInt());
        verify(notificationService).sendVotingReminderNotification(eq(nonVoter), eq(ballotWith2hEnd), anyInt());
    }
}
