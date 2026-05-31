package com.ballot.service;

import com.ballot.entity.Ballot;
import com.ballot.entity.MembershipStatus;
import com.ballot.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled jobs for the ballot lifecycle:
 * 1. Activate DRAFT ballots whose startDate has passed.
 * 2. Close ACTIVE ballots whose endDate has passed.
 * 3. Compute results for newly closed ballots.
 * 4. Notify members when ballots start or end.
 * 5. Cleanup expired tokens.
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

    /** Runs every 60 seconds to activate ballots. */
    @Scheduled(fixedDelay = 60_000)
    public void activatePendingBallots() {
        List<Ballot> drafts = ballotService.findDraftBallotsToActivate();
        if (drafts.isEmpty()) return;
        log.info("Activating {} draft ballot(s)", drafts.size());

        for (Ballot ballot : drafts) {
            try {
                ballotService.activateBallot(ballot);
                notifyOrganizationMembers(ballot, false);
                log.info("Ballot activated: {}", ballot.getId());
            } catch (Exception e) {
                log.error("Failed to activate ballot {}: {}", ballot.getId(), e.getMessage());
            }
        }
    }

    /** Runs every 60 seconds to close expired ballots and compute results. */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredBallots() {
        List<Ballot> actives = ballotService.findActiveBallotsToClose();
        if (actives.isEmpty()) return;
        log.info("Closing {} active ballot(s)", actives.size());

        for (Ballot ballot : actives) {
            try {
                ballotService.closeBallot(ballot);
                resultService.computeResults(ballot);
                notifyOrganizationMembers(ballot, true);
                log.info("Ballot closed and results computed: {}", ballot.getId());
            } catch (Exception e) {
                log.error("Failed to close ballot {}: {}", ballot.getId(), e.getMessage());
            }
        }
    }

    /** Clean up expired tokens daily at 2 AM. */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTokens() {
        int deleted = tokenService.cleanupExpiredTokens();
        log.info("Cleaned up {} expired token(s)", deleted);
    }

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
            log.error("Failed to send notifications for ballot {}: {}", ballot.getId(), e.getMessage());
        }
    }
}
