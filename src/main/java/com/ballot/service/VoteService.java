package com.ballot.service;

import com.ballot.dto.vote.CastVoteRequest;
import com.ballot.dto.vote.VoteResponse;
import com.ballot.entity.*;
import com.ballot.event.VoteCastEvent;
import com.ballot.exception.*;
import com.ballot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Voting service with optimistic lock for race condition prevention.
 * One vote per user per ballot enforced at both DB (unique constraint) and service layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final VoteRepository voteRepository;
    private final BallotRepository ballotRepository;
    private final BallotOptionRepository optionRepository;
    private final MembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VoteResponse castVote(String ballotId, CastVoteRequest request, User voter,
                                  String ipAddress, String userAgent) {
        Ballot ballot = ballotRepository.findById(ballotId)
                .orElseThrow(() -> new ResourceNotFoundException("Ballot", "id", ballotId));

        // Member check
        boolean isMember = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                voter.getId(), ballot.getOrganization().getId(), MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new UnauthorizedException("You must be an active member of this organization to vote.");
        }

        // Status check
        if (ballot.getStatus() != BallotStatus.ACTIVE) {
            throw new VotingPeriodException("This ballot is not currently active.");
        }

        // Date range check
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(ballot.getStartDate()) || now.isAfter(ballot.getEndDate())) {
            throw new VotingPeriodException("Voting is outside the allowed period.");
        }

        // Duplicate vote check
        if (voteRepository.existsByBallotIdAndUserId(ballotId, voter.getId())) {
            throw new DuplicateResourceException("You have already voted in this ballot.");
        }

        List<String> selectedIds = request.getSelectedOptionIds();
        if (!ballot.isMultipleChoice() && selectedIds.size() > 1) {
            throw new BadRequestException("This ballot does not allow multiple choices.");
        }

        List<Vote> savedVotes = new java.util.ArrayList<>();
        for (String optId : selectedIds) {
            BallotOption option = optionRepository.findById(optId)
                    .orElseThrow(() -> new ResourceNotFoundException("BallotOption", "id", optId));

            if (!option.getBallot().getId().equals(ballotId)) {
                throw new BadRequestException("The selected option does not belong to this ballot.");
            }

            Vote vote = Vote.builder()
                    .ballot(ballot)
                    .user(voter)
                    .option(option)
                    .build();
            savedVotes.add(voteRepository.save(vote));
        }

        if (!savedVotes.isEmpty()) {
            eventPublisher.publishEvent(new VoteCastEvent(this, voter.getId(), savedVotes.get(0).getId(),
                    ballotId, ipAddress, userAgent));
            log.info("Vote cast: user={}, ballot={}, options={}", voter.getId(), ballotId, selectedIds);
        }

        return toResponse(savedVotes, ballot.isAnonymous());
    }

    @Transactional(readOnly = true)
    public VoteResponse getMyVote(String ballotId, User voter) {
        java.util.List<Vote> votes = voteRepository.findAllByBallotIdAndUserId(ballotId, voter.getId());
        if (votes.isEmpty()) {
            throw new ResourceNotFoundException("You have not voted in this ballot.");
        }
        return toResponse(votes, false); // always show own vote
    }

    private VoteResponse toResponse(java.util.List<Vote> votes, boolean anonymous) {
        if (votes.isEmpty()) return null;
        Vote first = votes.get(0);
        java.util.List<String> optionIds = votes.stream().map(v -> v.getOption().getId()).toList();
        return VoteResponse.builder()
                .id(first.getId())
                .ballotId(first.getBallot().getId())
                .selectedOptionIds(optionIds)
                .userId(anonymous ? null : first.getUser().getId()) // privacy
                .createdAt(first.getSubmittedAt())
                .build();
    }
}
