package com.ballot.service;

import com.ballot.dto.ballot.*;
import com.ballot.dto.common.PageResponse;
import com.ballot.entity.*;
import com.ballot.event.BallotCreatedEvent;
import com.ballot.exception.*;
import com.ballot.repository.*;
import com.ballot.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BallotService {

    private final BallotRepository ballotRepository;
    private final BallotOptionRepository ballotOptionRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final VoteRepository voteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ResultService resultService;
    private final NotificationService notificationService;
    private final DynamicBallotScheduler dynamicScheduler;

    // ===== CREATE =====

    @Transactional
    public BallotResponse createBallot(CreateBallotRequest request, User creator,
                                        String ipAddress, String userAgent) {
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", request.getOrganizationId()));

        requireAdminOrOwner(org.getId(), creator.getId());
        
        LocalDateTime start = request.isStartImmediately() ? LocalDateTime.now() : request.getStartDate();
        if (start == null) {
            throw new BadRequestException("Start date is required if not starting immediately.");
        }
        if (!request.isStartImmediately() && start.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Start date must be in the future.");
        }
        validateDates(start, request.getEndDate());

        BallotStatus initialStatus = request.isStartImmediately() ? BallotStatus.ACTIVE : BallotStatus.DRAFT;

        Ballot ballot = Ballot.builder()
                .organization(org)
                .title(request.getTitle())
                .description(request.getDescription())
                .anonymous(request.isAnonymous())
                .multipleChoice(request.isMultipleChoice())
                .startDate(start)
                .endDate(request.getEndDate())
                .status(initialStatus)
                .createdBy(creator)
                .options(new ArrayList<>())
                .build();
        ballot = ballotRepository.save(ballot);

        List<BallotOption> options = buildOptions(request.getOptions(), ballot);
        ballot.getOptions().addAll(ballotOptionRepository.saveAll(options));

        // Dynamically schedule: activate, close, reminders (24h + 1h), results publication
        dynamicScheduler.scheduleAll(ballot);

        eventPublisher.publishEvent(new BallotCreatedEvent(this, creator.getId(), ballot.getId(),
                org.getId(), ipAddress, userAgent));
        log.info("Ballot created: {} in org {}", ballot.getId(), org.getId());
        return toResponse(ballot, creator, 0, false);
    }

    // ===== READ =====

    @Transactional(readOnly = true)
    public PageResponse<BallotResponse> getBallotsByOrg(String orgId, User viewer,
                                                         int page, int size,
                                                         String sortBy, String direction) {
        requireActiveMember(orgId, viewer.getId());
        // SORTING: PageUtil.of() builds Sort.by(sortBy).ascending/descending()
        Pageable pageable = PageUtil.of(page, size, sortBy, direction);
        Page<Ballot> ballots = ballotRepository.findAllByOrganizationId(orgId, pageable);
        return PageUtil.toPageResponse(ballots.map(b -> {
            long totalVotes = voteRepository.countByBallotId(b.getId());
            boolean hasVoted = voteRepository.existsByBallotIdAndUserId(b.getId(), viewer.getId());
            return toResponse(b, b.getCreatedBy(), totalVotes, hasVoted);
        }));
    }

    @Transactional(readOnly = true)
    public BallotResponse getBallotById(String ballotId, User viewer) {
        Ballot ballot = findBallot(ballotId);
        requireActiveMember(ballot.getOrganization().getId(), viewer.getId());
        long totalVotes = voteRepository.countByBallotId(ballotId);
        boolean hasVoted = voteRepository.existsByBallotIdAndUserId(ballotId, viewer.getId());
        return toResponse(ballot, ballot.getCreatedBy(), totalVotes, hasVoted);
    }

    @Transactional(readOnly = true)
    public PageResponse<BallotResponse> getVisibleBallotsForUser(User user,
                                                                   int page, int size,
                                                                   String sortBy, String direction) {
        // PAGINATION + SORTING: Pageable carries both page/size and the chosen sort
        Pageable pageable = PageUtil.of(page, size, sortBy, direction);
        Page<Ballot> ballots = ballotRepository.findVisibleBallotsForUser(user.getId(), pageable);
        return PageUtil.toPageResponse(ballots.map(b -> {
            long totalVotes = voteRepository.countByBallotId(b.getId());
            boolean hasVoted = voteRepository.existsByBallotIdAndUserId(b.getId(), user.getId());
            return toResponse(b, b.getCreatedBy(), totalVotes, hasVoted);
        }));
    }

    @Transactional(readOnly = true)
    public PageResponse<BallotResponse> getMyBallots(User user,
                                                      int page, int size,
                                                      String sortBy, String direction) {
        Pageable pageable = PageUtil.of(page, size, sortBy, direction);
        Page<Ballot> ballots = ballotRepository.findAllByCreatedById(user.getId(), pageable);
        return PageUtil.toPageResponse(ballots.map(b -> {
            long totalVotes = voteRepository.countByBallotId(b.getId());
            boolean hasVoted = voteRepository.existsByBallotIdAndUserId(b.getId(), user.getId());
            return toResponse(b, b.getCreatedBy(), totalVotes, hasVoted);
        }));
    }

    // ===== UPDATE =====

    @Transactional
    public BallotResponse updateBallot(String ballotId, UpdateBallotRequest request, User actor) {
        Ballot ballot = findBallot(ballotId);
        requireAdminOrOwner(ballot.getOrganization().getId(), actor.getId());

        if (!ballot.isEditable()) {
            throw new BallotNotEditableException("Ballot cannot be edited because it is " + ballot.getStatus());
        }

        if (request.getTitle() != null) ballot.setTitle(request.getTitle());
        if (request.getDescription() != null) ballot.setDescription(request.getDescription());
        if (request.getAnonymous() != null) ballot.setAnonymous(request.getAnonymous());
        if (request.getMultipleChoice() != null) ballot.setMultipleChoice(request.getMultipleChoice());
        if (request.getStartDate() != null) ballot.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) ballot.setEndDate(request.getEndDate());

        LocalDateTime newStart = ballot.getStartDate();
        LocalDateTime newEnd = ballot.getEndDate();
        validateDates(newStart, newEnd);

        if (request.getOptions() != null) {
            ballotOptionRepository.deleteAllByBallotId(ballotId);
            ballot.getOptions().clear();
            List<BallotOption> newOptions = buildOptions(request.getOptions(), ballot);
            ballot.getOptions().addAll(ballotOptionRepository.saveAll(newOptions));
        }

        ballot = ballotRepository.save(ballot);

        // Reschedule all tasks with updated start/end times
        dynamicScheduler.scheduleAll(ballot);

        long totalVotes = voteRepository.countByBallotId(ballotId);
        return toResponse(ballot, ballot.getCreatedBy(), totalVotes, false);
    }

    // ===== CLOSE (MANUAL) =====

    @Transactional
    public void manuallyCloseBallot(String ballotId, User actor) {
        Ballot ballot = findBallot(ballotId);
        requireAdminOrOwner(ballot.getOrganization().getId(), actor.getId());

        if (ballot.getStatus() != BallotStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE ballots can be closed.");
        }

        ballot.setStatus(BallotStatus.CLOSED);
        ballot.setEndDate(LocalDateTime.now()); // Update end date to reflect early closure
        ballotRepository.save(ballot);

        resultService.computeResults(ballot);
        log.info("Ballot {} was manually closed by {}", ballotId, actor.getId());

        // Notify all active members
        List<Membership> members = membershipRepository.findAllByOrganizationIdAndStatus(
                ballot.getOrganization().getId(), MembershipStatus.ACTIVE);
        for (Membership membership : members) {
            if (!membership.getUser().getId().equals(actor.getId())) { // Optional: Don't notify the person who closed it, or just notify everyone
                notificationService.sendBallotEndedNotification(membership.getUser(), ballot);
            }
        }
    }

    // ===== DELETE =====

    @Transactional
    public void deleteBallot(String ballotId, User actor) {
        Ballot ballot = findBallot(ballotId);
        requireAdminOrOwner(ballot.getOrganization().getId(), actor.getId());

        if (!ballot.isEditable()) {
            throw new BallotNotEditableException("Only DRAFT ballots can be deleted.");
        }
        ballot.setStatus(BallotStatus.CANCELLED);
        ballotRepository.save(ballot);
        // Cancel any pending dynamic tasks for this ballot
        dynamicScheduler.cancelAllTasksForBallot(ballotId);
    }

    // ===== INTERNAL (used by scheduler) =====

    @Transactional
    public List<Ballot> findDraftBallotsToActivate() {
        return ballotRepository.findDraftBallotsToActivate(LocalDateTime.now());
    }

    @Transactional
    public List<Ballot> findActiveBallotsToClose() {
        return ballotRepository.findActiveBallotsToClose(LocalDateTime.now());
    }

    @Transactional
    public void activateBallot(Ballot ballot) {
        ballot.setStatus(BallotStatus.ACTIVE);
        ballotRepository.save(ballot);
    }

    @Transactional
    public void closeBallot(Ballot ballot) {
        ballot.setStatus(BallotStatus.CLOSED);
        ballotRepository.save(ballot);
    }

    // ===== HELPERS =====

    public Ballot findBallot(String ballotId) {
        return ballotRepository.findById(ballotId)
                .orElseThrow(() -> new ResourceNotFoundException("Ballot", "id", ballotId));
    }

    private void validateDates(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("End date must be after start date.");
        }
    }

    private List<BallotOption> buildOptions(List<BallotOptionRequest> requests, Ballot ballot) {
        List<BallotOption> options = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            options.add(BallotOption.builder()
                    .ballot(ballot)
                    .optionText(requests.get(i).getOptionText())
                    .displayOrder(i)
                    .build());
        }
        return options;
    }

    private void requireActiveMember(String orgId, String userId) {
        boolean isMember = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                userId, orgId, MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new org.springframework.security.access.AccessDeniedException("You must be an active member of this organization to view its ballots.");
        }
    }

    private void requireAdminOrOwner(String orgId, String userId) {
        membershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .filter(m -> (m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.ADMIN)
                        && m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Only organization admins can manage ballots."));
    }

    private BallotResponse toResponse(Ballot ballot, User creator, long totalVotes, boolean hasVoted) {
        List<BallotOptionResponse> optionResponses = ballot.getOptions().stream()
                .map(o -> BallotOptionResponse.builder()
                        .id(o.getId())
                        .optionText(o.getOptionText())
                        .displayOrder(o.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());

        return BallotResponse.builder()
                .id(ballot.getId())
                .organizationId(ballot.getOrganization().getId())
                .organizationName(ballot.getOrganization().getName())
                .title(ballot.getTitle())
                .description(ballot.getDescription())
                .anonymous(ballot.isAnonymous())
                .multipleChoice(ballot.isMultipleChoice())
                .startDate(ballot.getStartDate())
                .endDate(ballot.getEndDate())
                .status(ballot.getStatus())
                .createdById(creator.getId())
                .createdByName(creator.getFullName())
                .options(optionResponses)
                .totalVotes(totalVotes)
                .hasVoted(hasVoted)
                .createdAt(ballot.getCreatedAt())
                .build();
    }
}
