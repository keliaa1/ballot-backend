package com.ballot.repository;

import com.ballot.entity.Ballot;
import com.ballot.entity.BallotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BallotRepository extends JpaRepository<Ballot, String> {

    Page<Ballot> findAllByOrganizationId(String organizationId, Pageable pageable);

    Page<Ballot> findAllByOrganizationIdAndStatus(String organizationId, BallotStatus status, Pageable pageable);

    /** Find DRAFT ballots whose start date has passed — to be activated by scheduler. */
    @Query("SELECT b FROM Ballot b WHERE b.status = 'DRAFT' AND b.startDate <= :now")
    List<Ballot> findDraftBallotsToActivate(LocalDateTime now);

    /** Find ACTIVE ballots whose end date has passed — to be closed by scheduler. */
    @Query("SELECT b FROM Ballot b WHERE b.status = 'ACTIVE' AND b.endDate <= :now")
    List<Ballot> findActiveBallotsToClose(LocalDateTime now);

    /** Ballots created by the specified user. */
    Page<Ballot> findAllByCreatedById(String createdById, Pageable pageable);

    /** Ballots in organisations the user belongs to (active memberships). */
    @Query("""
            SELECT b FROM Ballot b
            JOIN Membership m ON m.organization = b.organization
            WHERE m.user.id = :userId
            AND m.status = com.ballot.entity.MembershipStatus.ACTIVE
            AND b.status != com.ballot.entity.BallotStatus.DRAFT
            """)
    Page<Ballot> findVisibleBallotsForUser(String userId, Pageable pageable);
}
