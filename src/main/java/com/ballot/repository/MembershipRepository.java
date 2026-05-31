package com.ballot.repository;

import com.ballot.entity.Membership;
import com.ballot.entity.MembershipRole;
import com.ballot.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, String> {

    Optional<Membership> findByUserIdAndOrganizationId(String userId, String organizationId);

    boolean existsByUserIdAndOrganizationIdAndStatus(String userId, String organizationId, MembershipStatus status);

    Page<Membership> findAllByOrganizationIdAndStatus(String organizationId, MembershipStatus status, Pageable pageable);

    List<Membership> findAllByOrganizationIdAndStatus(String organizationId, MembershipStatus status);

    List<Membership> findAllByOrganizationIdAndStatusAndRole(
            String organizationId, MembershipStatus status, MembershipRole role);

    long countByOrganizationIdAndStatus(String organizationId, MembershipStatus status);

    @Query("""
            SELECT m FROM Membership m
            WHERE m.organization.id = :orgId
            AND m.status = com.ballot.entity.MembershipStatus.PENDING
            """)
    Page<Membership> findPendingByOrganizationId(String orgId, Pageable pageable);
}
