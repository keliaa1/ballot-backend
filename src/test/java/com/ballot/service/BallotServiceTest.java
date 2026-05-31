package com.ballot.service;

import com.ballot.dto.ballot.BallotOptionRequest;
import com.ballot.dto.ballot.CreateBallotRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BallotServiceTest {

    @Mock private BallotRepository ballotRepository;
    @Mock private BallotOptionRepository ballotOptionRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BallotService ballotService;

    private Organization org;
    private User admin;
    private Membership adminMembership;

    @BeforeEach
    void setUp() {
        org = Organization.builder().id("org-1").name("Test Org").status(OrganizationStatus.ACTIVE).build();
        admin = User.builder().id("admin-1").email("admin@test.com").firstName("Admin").lastName("User").build();
        adminMembership = Membership.builder()
                .id("mem-1").user(admin).organization(org)
                .role(MembershipRole.ADMIN).status(MembershipStatus.ACTIVE)
                .build();
    }

    @Test
    void createBallot_shouldSucceedForAdmin() {
        CreateBallotRequest request = buildCreateRequest();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId("admin-1", "org-1"))
                .thenReturn(Optional.of(adminMembership));

        Ballot savedBallot = Ballot.builder()
                .id("ballot-1").organization(org).title("Test Ballot")
                .status(BallotStatus.DRAFT).createdBy(admin)
                .options(new ArrayList<>())
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(2))
                .build();
        when(ballotRepository.save(any())).thenReturn(savedBallot);
        when(ballotOptionRepository.saveAll(any())).thenReturn(List.of());

        var response = ballotService.createBallot(request, admin, "127.0.0.1", "agent");

        assertThat(response.getStatus()).isEqualTo(BallotStatus.DRAFT);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createBallot_shouldThrowWhenEndBeforeStart() {
        CreateBallotRequest request = buildCreateRequest();
        // Set end date before start date
        setField(request, "endDate", LocalDateTime.now().plusDays(1));
        setField(request, "startDate", LocalDateTime.now().plusDays(2));

        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId("admin-1", "org-1"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> ballotService.createBallot(request, admin, "127.0.0.1", "agent"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    void deleteBallot_shouldThrowForActiveBallot() {
        Ballot active = Ballot.builder()
                .id("ballot-1").organization(org).status(BallotStatus.ACTIVE).createdBy(admin)
                .options(new ArrayList<>()).build();
        when(ballotRepository.findById("ballot-1")).thenReturn(Optional.of(active));
        when(membershipRepository.findByUserIdAndOrganizationId("admin-1", "org-1"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> ballotService.deleteBallot("ballot-1", admin))
                .isInstanceOf(BallotNotEditableException.class);
    }

    @Test
    void createBallot_shouldThrowForNonAdmin() {
        Membership memberMembership = Membership.builder()
                .id("mem-2").user(admin).organization(org)
                .role(MembershipRole.MEMBER).status(MembershipStatus.ACTIVE)
                .build();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId("admin-1", "org-1"))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> ballotService.createBallot(buildCreateRequest(), admin, "127.0.0.1", "agent"))
                .isInstanceOf(UnauthorizedException.class);
    }

    private CreateBallotRequest buildCreateRequest() {
        CreateBallotRequest request = new CreateBallotRequest();
        setField(request, "title", "Test Ballot");
        setField(request, "organizationId", "org-1");
        setField(request, "startDate", LocalDateTime.now().plusDays(1));
        setField(request, "endDate", LocalDateTime.now().plusDays(2));
        List<BallotOptionRequest> options = new ArrayList<>();
        BallotOptionRequest opt1 = new BallotOptionRequest();
        BallotOptionRequest opt2 = new BallotOptionRequest();
        setField(opt1, "optionText", "Option A");
        setField(opt2, "optionText", "Option B");
        options.add(opt1);
        options.add(opt2);
        setField(request, "options", options);
        return request;
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
