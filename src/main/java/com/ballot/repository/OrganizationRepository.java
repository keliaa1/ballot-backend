package com.ballot.repository;

import com.ballot.entity.Organization;
import com.ballot.entity.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByInviteCode(String inviteCode);

    Optional<Organization> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    Page<Organization> findAllByStatus(OrganizationStatus status, Pageable pageable);

    @Query("""
            SELECT o FROM Organization o
            JOIN Membership m ON m.organization = o
            WHERE m.user.id = :userId
            AND m.status = com.ballot.entity.MembershipStatus.ACTIVE
            """)
    Page<Organization> findAllByMemberUserId(String userId, Pageable pageable);

    @Query("""
            SELECT o FROM Organization o
            WHERE o.status = com.ballot.entity.OrganizationStatus.ACTIVE
            AND (LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(o.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Organization> searchActiveOrganizations(String search, Pageable pageable);
}
