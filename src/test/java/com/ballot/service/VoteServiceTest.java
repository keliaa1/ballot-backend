package com.ballot.service;

import com.ballot.dto.vote.CastVoteRequest;
import com.ballot.entity.*;
import com.ballot.exception.*;
import com.ballot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock private VoteRepository voteRepository;
    @Mock private BallotRepository ballotRepository;
    @Mock private BallotOptionRepository optionRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VoteService voteService;

    private User voter;
    private Ballot ballot;
    private BallotOption option;
    private Organization org;

    @BeforeEach
    void setUp() {
        org = Organization.builder().id("org-1").name("Test Org").build();

        ballot = Ballot.builder()
                .id("ballot-1")
                .organization(org)
                .title("Test Ballot")
                .status(BallotStatus.ACTIVE)
                .anonymous(false)
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .build();

        option = BallotOption.builder()
                .id("option-1")
                .ballot(ballot)
                .optionText("Option A")
                .build();

        voter = User.builder()
                .id("user-1")
                .email("voter@test.com")
                .build();
    }

    @Test
    void castVote_shouldSucceedForActiveBallot() {
        CastVoteRequest request = new CastVoteRequest();
        setField(request, "selectedOptionIds", java.util.List.of("option-1"));

        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(ballot));
        when(membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                "user-1", "org-1", MembershipStatus.ACTIVE)).thenReturn(true);
        when(voteRepository.existsByBallotIdAndUserId("ballot-1", "user-1")).thenReturn(false);
        when(optionRepository.findById("option-1")).thenReturn(Optional.of(option));
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> {
            Vote v = inv.getArgument(0);
            setField(v, "id", "vote-1");
            return v;
        });

        var response = voteService.castVote("ballot-1", request, voter, "127.0.0.1", "agent");

        assertThat(response.getBallotId()).isEqualTo("ballot-1");
        assertThat(response.getSelectedOptionIds()).containsExactly("option-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void castVote_shouldThrowForDuplicateVote() {
        CastVoteRequest request = new CastVoteRequest();
        setField(request, "selectedOptionIds", java.util.List.of("option-1"));

        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(ballot));
        when(membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                "user-1", "org-1", MembershipStatus.ACTIVE)).thenReturn(true);
        when(voteRepository.existsByBallotIdAndUserId("ballot-1", "user-1")).thenReturn(true);

        assertThatThrownBy(() -> voteService.castVote("ballot-1", request, voter, "127.0.0.1", "agent"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already voted");
    }

    @Test
    void castVote_shouldThrowForInactiveBallot() {
        ballot.setStatus(BallotStatus.CLOSED);
        CastVoteRequest request = new CastVoteRequest();
        setField(request, "selectedOptionIds", java.util.List.of("option-1"));

        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(ballot));
        when(membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                "user-1", "org-1", MembershipStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> voteService.castVote("ballot-1", request, voter, "127.0.0.1", "agent"))
                .isInstanceOf(VotingPeriodException.class);
    }

    @Test
    void castVote_shouldThrowForNonMember() {
        CastVoteRequest request = new CastVoteRequest();
        setField(request, "selectedOptionIds", java.util.List.of("option-1"));

        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(ballot));
        when(membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                "user-1", "org-1", MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> voteService.castVote("ballot-1", request, voter, "127.0.0.1", "agent"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void castVote_shouldHideUserIdForAnonymousBallot() {
        ballot.setAnonymous(true);
        CastVoteRequest request = new CastVoteRequest();
        setField(request, "selectedOptionIds", java.util.List.of("option-1"));

        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(ballot));
        when(membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                "user-1", "org-1", MembershipStatus.ACTIVE)).thenReturn(true);
        when(voteRepository.existsByBallotIdAndUserId("ballot-1", "user-1")).thenReturn(false);
        when(optionRepository.findById("option-1")).thenReturn(Optional.of(option));
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> {
            Vote v = inv.getArgument(0);
            setField(v, "id", "vote-1");
            return v;
        });

        var response = voteService.castVote("ballot-1", request, voter, "127.0.0.1", "agent");

        assertThat(response.getUserId()).isNull();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
