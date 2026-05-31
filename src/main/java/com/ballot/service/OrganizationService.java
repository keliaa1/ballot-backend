package com.ballot.service;

import com.ballot.dto.common.PageResponse;
import com.ballot.dto.organization.*;
import com.ballot.entity.*;
import com.ballot.event.OrgCreatedEvent;
import com.ballot.exception.*;
import com.ballot.repository.*;
import com.ballot.util.CloudinaryService;
import com.ballot.util.InviteCodeGenerator;
import com.ballot.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final CloudinaryService cloudinaryService;
    private final ApplicationEventPublisher eventPublisher;

    // ===== CREATE =====

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, User creator,
                                                    String ipAddress, String userAgent) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Organization name '" + request.getName() + "' is already taken.");
        }

        String inviteCode = generateUniqueInviteCode();
        Organization org = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .inviteCode(inviteCode)
                .joinType(request.getJoinType() != null ? request.getJoinType() : JoinType.PUBLIC)
                .createdBy(creator)
                .status(OrganizationStatus.ACTIVE)
                .build();
        org = organizationRepository.save(org);

        // Creator automatically becomes OWNER
        Membership ownership = Membership.builder()
                .user(creator)
                .organization(org)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        membershipRepository.save(ownership);

        eventPublisher.publishEvent(new OrgCreatedEvent(this, creator.getId(), org.getId(), ipAddress, userAgent));
        log.info("Organization created: {} by {}", org.getName(), creator.getEmail());
        return toResponse(org, 1L, MembershipRole.OWNER);
    }

    // ===== READ =====

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> getAllOrganizations(int page, int size, String search) {
        Pageable pageable = PageUtil.of(page, size, "createdAt", "desc");
        Page<Organization> orgPage;
        if (search != null && !search.isBlank()) {
            orgPage = organizationRepository.searchActiveOrganizations(search, pageable);
        } else {
            orgPage = organizationRepository.findAllByStatus(OrganizationStatus.ACTIVE, pageable);
        }
        return PageUtil.toPageResponse(orgPage.map(o -> toResponse(o,
                membershipRepository.countByOrganizationIdAndStatus(o.getId(), MembershipStatus.ACTIVE), null)));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> getMyOrganizations(User user, int page, int size) {
        Pageable pageable = PageUtil.of(page, size, "createdAt", "desc");
        Page<Organization> orgPage = organizationRepository.findAllByMemberUserId(user.getId(), pageable);
        return PageUtil.toPageResponse(orgPage.map(o -> {
            long memberCount = membershipRepository.countByOrganizationIdAndStatus(o.getId(), MembershipStatus.ACTIVE);
            MembershipRole role = membershipRepository.findByUserIdAndOrganizationId(user.getId(), o.getId())
                    .map(Membership::getRole).orElse(null);
            return toResponse(o, memberCount, role);
        }));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(String orgId) {
        Organization org = findOrgById(orgId);
        long memberCount = membershipRepository.countByOrganizationIdAndStatus(orgId, MembershipStatus.ACTIVE);
        return toResponse(org, memberCount, null);
    }

    // ===== UPDATE =====

    @Transactional
    public OrganizationResponse updateOrganization(String orgId, UpdateOrganizationRequest request,
                                                    User actor) {
        Organization org = findOrgById(orgId);
        requireAdminOrOwner(orgId, actor.getId());

        if (request.getName() != null && organizationRepository.existsByNameAndIdNot(request.getName(), orgId)) {
            throw new DuplicateResourceException("Organization name '" + request.getName() + "' is already taken.");
        }
        if (request.getName() != null) org.setName(request.getName());
        if (request.getDescription() != null) org.setDescription(request.getDescription());
        if (request.getJoinType() != null) org.setJoinType(request.getJoinType());

        org = organizationRepository.save(org);
        long memberCount = membershipRepository.countByOrganizationIdAndStatus(orgId, MembershipStatus.ACTIVE);
        return toResponse(org, memberCount, null);
    }

    @Transactional
    public String uploadLogo(String orgId, MultipartFile file, User actor) throws IOException {
        Organization org = findOrgById(orgId);
        requireOwner(orgId, actor.getId());

        if (org.getLogoPublicId() != null) {
            cloudinaryService.deleteImage(org.getLogoPublicId());
        }
        CloudinaryService.UploadResult result = cloudinaryService.uploadImage(file, "ballot/org-logos");
        org.setLogoUrl(result.secureUrl());
        org.setLogoPublicId(result.publicId());
        organizationRepository.save(org);
        return result.secureUrl();
    }

    // ===== DELETE =====

    @Transactional
    public void deleteOrganization(String orgId, User actor) {
        Organization org = findOrgById(orgId);
        requireOwner(orgId, actor.getId());

        org.setStatus(OrganizationStatus.DELETED);
        organizationRepository.save(org);
        log.info("Organization soft-deleted: {} by {}", orgId, actor.getEmail());
    }

    // ===== JOIN / LEAVE =====

    @Transactional
    public MembershipStatus joinOrganization(JoinOrganizationRequest request, User user) {
        Organization org;

        if (request.getInviteCode() != null && !request.getInviteCode().isBlank()) {
            // Look up org by invite code to validate it
            Organization orgByCode = organizationRepository.findByInviteCode(request.getInviteCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code."));

            // If organizationId is also provided, verify it matches
            if (request.getOrganizationId() != null && !request.getOrganizationId().isBlank()) {
                if (!orgByCode.getId().equals(request.getOrganizationId())) {
                    throw new BadRequestException("The invite code does not match this organization.");
                }
            }
            org = orgByCode;
        } else {
            org = findOrgById(request.getOrganizationId());
        }

        if (org.getStatus() != OrganizationStatus.ACTIVE) {
            throw new BadRequestException("This organization is not active.");
        }

        // INVITE_ONLY organizations require a valid invite code
        if (org.getJoinType() == JoinType.INVITE_ONLY
                && (request.getInviteCode() == null || request.getInviteCode().isBlank())) {
            throw new BadRequestException("This organization requires an invite code to join.");
        }

        // Check existing membership
        boolean alreadyMember = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                user.getId(), org.getId(), MembershipStatus.ACTIVE);
        if (alreadyMember) {
            throw new DuplicateResourceException("You are already a member of this organization.");
        }

        boolean hasPendingRequest = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                user.getId(), org.getId(), MembershipStatus.PENDING);
        if (hasPendingRequest) {
            throw new DuplicateResourceException("You already have a pending join request for this organization.");
        }

        // Determine membership status based on join type
        MembershipStatus memberStatus;
        if (org.getJoinType() == JoinType.PUBLIC) {
            memberStatus = MembershipStatus.ACTIVE;
        } else if (org.getJoinType() == JoinType.INVITE_ONLY && request.getInviteCode() != null) {
            memberStatus = MembershipStatus.ACTIVE;
        } else {
            memberStatus = MembershipStatus.PENDING;
        }

        Membership membership = Membership.builder()
                .user(user)
                .organization(org)
                .role(MembershipRole.MEMBER)
                .status(memberStatus)
                .joinedAt(memberStatus == MembershipStatus.ACTIVE ? LocalDateTime.now() : null)
                .build();
        membershipRepository.save(membership);
        return memberStatus;
    }

    @Transactional
    public void leaveOrganization(String orgId, User user) {
        Membership membership = membershipRepository.findByUserIdAndOrganizationId(user.getId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("You are not a member of this organization."));

        if (membership.getRole() == MembershipRole.OWNER) {
            throw new BadRequestException("Owners cannot leave their organization. Transfer ownership or delete the organization first.");
        }

        membership.setStatus(MembershipStatus.LEFT);
        membershipRepository.save(membership);
    }

    // ===== HELPERS =====

    private Organization findOrgById(String orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", orgId));
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

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = inviteCodeGenerator.generate();
        } while (organizationRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private OrganizationResponse toResponse(Organization org, long memberCount, MembershipRole myRole) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .description(org.getDescription())
                .logoUrl(org.getLogoUrl())
                .inviteCode(org.getInviteCode())
                .joinType(org.getJoinType())
                .createdById(org.getCreatedBy().getId())
                .createdByName(org.getCreatedBy().getFullName())
                .status(org.getStatus())
                .memberCount(memberCount)
                .myRole(myRole)
                .createdAt(org.getCreatedAt())
                .build();
    }
}
