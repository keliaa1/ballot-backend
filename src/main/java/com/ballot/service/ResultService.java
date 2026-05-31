package com.ballot.service;

import com.ballot.dto.result.BallotResultResponse;
import com.ballot.dto.result.ResultResponse;
import com.ballot.entity.*;
import com.ballot.exception.*;
import com.ballot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes and retrieves ballot results.
 * Results are visible only after the ballot is CLOSED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultService {

    private final ResultRepository resultRepository;
    private final BallotRepository ballotRepository;
    private final BallotOptionRepository optionRepository;
    private final VoteRepository voteRepository;
    private final MembershipRepository membershipRepository;

    // ===== COMPUTE (called by scheduler) =====

    @Transactional
    public void computeResults(Ballot ballot) {
        if (resultRepository.existsByBallotId(ballot.getId())) {
            log.info("Results already computed for ballot: {}", ballot.getId());
            return;
        }

        List<BallotOption> options = optionRepository.findAllByBallotIdOrderByDisplayOrder(ballot.getId());
        long totalVotes = voteRepository.countByBallotId(ballot.getId());

        // Count votes per option
        List<Object[]> voteCounts = voteRepository.countVotesByOptionForBallot(ballot.getId());
        Map<String, Long> countMap = voteCounts.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));

        List<Result> results = new ArrayList<>();
        for (BallotOption option : options) {
            long count = countMap.getOrDefault(option.getId(), 0L);
            BigDecimal percentage = totalVotes > 0
                    ? BigDecimal.valueOf(count * 100.0 / totalVotes).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            results.add(Result.builder()
                    .ballot(ballot)
                    .option(option)
                    .voteCount(count)
                    .percentage(percentage)
                    .build());
        }

        resultRepository.saveAll(results);
        log.info("Results computed for ballot {}: {} total votes across {} options",
                ballot.getId(), totalVotes, options.size());
    }

    // ===== GET RESULTS =====

    @Transactional(readOnly = true)
    public BallotResultResponse getResults(String ballotId, User viewer) {
        Ballot ballot = ballotRepository.findById(ballotId)
                .orElseThrow(() -> new ResourceNotFoundException("Ballot", "id", ballotId));

        // Only active members can see results
        boolean isMember = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                viewer.getId(), ballot.getOrganization().getId(), MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new UnauthorizedException("You must be a member of this organization to view results.");
        }

        if (ballot.getStatus() != BallotStatus.CLOSED) {
            throw new BadRequestException("Results are only available after the ballot has closed.");
        }

        List<Result> results = resultRepository.findAllByBallotIdOrderByVoteCountDesc(ballotId);
        long totalVotes = results.stream().mapToLong(Result::getVoteCount).sum();

        List<ResultResponse> resultResponses = results.stream()
                .map(r -> ResultResponse.builder()
                        .optionId(r.getOption().getId())
                        .optionText(r.getOption().getOptionText())
                        .voteCount(r.getVoteCount())
                        .percentage(r.getPercentage())
                        .computedAt(r.getComputedAt())
                        .build())
                .collect(Collectors.toList());

        return BallotResultResponse.builder()
                .ballotId(ballot.getId())
                .ballotTitle(ballot.getTitle())
                .status(ballot.getStatus())
                .totalVotes(totalVotes)
                .results(resultResponses)
                .closedAt(ballot.getEndDate())
                .build();
    }
}
