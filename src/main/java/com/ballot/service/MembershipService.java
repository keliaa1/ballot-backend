package com.ballot.service;

import com.ballot.dto.common.PageResponse;
import com.ballot.dto.membership.MembershipResponse;
import com.ballot.dto.membership.UpdateMemberRoleRequest;
import com.ballot.entity.*;
import com.ballot.event.RoleChangedEvent;
import com.ballot.exception.*;
import com.ballot.repository.MembershipRepository;
import com.ballot.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> getMembers(String orgId,
                                                        int page, int size,
                                                        String sortBy, String direction) {
        // PAGINATION + SORTING: Pageable bundles page number, page size, and sort together.
        // Spring Data JPA translates this into: ORDER BY <sortBy> <direction> LIMIT <size> OFFSET <page*size>
        Pageable pageable = PageUtil.of(page, size, sortBy, direction);
        Page<Membership> memberships = membershipRepository
                .findAllByOrganizationIdAndStatus(orgId, MembershipStatus.ACTIVE, pageable);
        return PageUtil.toPageResponse(memberships.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> getPendingRequests(String orgId, User actor, int page, int size) {
        requireAdminOrOwner(orgId, actor.getId());
        Pageable pageable = PageUtil.of(page, size);
        Page<Membership> pending = membershipRepository.findPendingByOrganizationId(orgId, pageable);
        return PageUtil.toPageResponse(pending.map(this::toResponse));
    }

    @Transactional
    public MembershipResponse approveMembership(String orgId, String membershipId, User actor) {
        requireAdminOrOwner(orgId, actor.getId());
        Membership membership = findMembership(membershipId);
        validateBelongsToOrg(membership, orgId);

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new BadRequestException("Membership request is not in PENDING state.");
        }
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        Membership saved = membershipRepository.save(membership);
        
        notificationService.sendMembershipApprovedNotification(saved.getUser(), saved.getOrganization());
        
        return toResponse(saved);
    }

    @Transactional
    public MembershipResponse rejectMembership(String orgId, String membershipId, User actor) {
        requireAdminOrOwner(orgId, actor.getId());
        Membership membership = findMembership(membershipId);
        validateBelongsToOrg(membership, orgId);

        membership.setStatus(MembershipStatus.REJECTED);
        Membership saved = membershipRepository.save(membership);
        
        notificationService.sendMembershipRejectedNotification(saved.getUser(), saved.getOrganization());
        
        return toResponse(saved);
    }

    @Transactional
    public MembershipResponse updateMemberRole(String orgId, String targetUserId,
                                                UpdateMemberRoleRequest request,
                                                User actor, String ipAddress, String userAgent) {
        requireOwner(orgId, actor.getId());

        Membership membership = membershipRepository.findByUserIdAndOrganizationId(targetUserId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found for user " + targetUserId));

        if (membership.getRole() == MembershipRole.OWNER) {
            throw new BadRequestException("Cannot change the role of the organization owner.");
        }
        if (request.getRole() == MembershipRole.OWNER) {
            throw new BadRequestException("Use the transfer ownership endpoint to assign OWNER role.");
        }

        membership.setRole(request.getRole());
        Membership saved = membershipRepository.save(membership);
        eventPublisher.publishEvent(new RoleChangedEvent(this, actor.getId(), targetUserId,
                orgId, request.getRole().name(), ipAddress, userAgent));
        return toResponse(saved);
    }

    @Transactional
    public void removeMember(String orgId, String targetUserId, User actor) {
        requireOwnerOrSelfAdminAction(orgId, actor.getId(), targetUserId);

        Membership membership = membershipRepository.findByUserIdAndOrganizationId(targetUserId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this organization."));

        if (membership.getRole() == MembershipRole.OWNER) {
            throw new BadRequestException("Cannot remove the organization owner.");
        }

        membership.setStatus(MembershipStatus.REMOVED);
        membershipRepository.save(membership);
    }

    // ===== HELPERS =====

    private Membership findMembership(String membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", "id", membershipId));
    }

    private void validateBelongsToOrg(Membership membership, String orgId) {
        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new BadRequestException("Membership does not belong to this organization.");
        }
    }

    private void requireOwner(String orgId, String userId) {
        membershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .filter(m -> m.getRole() == MembershipRole.OWNER && m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Only the organization owner can perform this action."));
    }

    private void requireAdminOrOwner(String orgId, String userId) {
        membershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .filter(m -> (m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.ADMIN)
                        && m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Only organization admins can perform this action."));
    }

    private void requireOwnerOrSelfAdminAction(String orgId, String actorId, String targetId) {
        membershipRepository.findByUserIdAndOrganizationId(actorId, orgId)
                .filter(m -> (m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.ADMIN)
                        && m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("You are not authorized to remove members."));
    }

    private MembershipResponse toResponse(Membership m) {
        User user = m.getUser();
        return MembershipResponse.builder()
                .membershipId(m.getId())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .role(m.getRole())
                .status(m.getStatus())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
