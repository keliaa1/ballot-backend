package com.ballot.controller;

import com.ballot.dto.common.ApiResponse;
import com.ballot.dto.common.PageResponse;
import com.ballot.dto.membership.MembershipResponse;
import com.ballot.dto.membership.UpdateMemberRoleRequest;
import com.ballot.dto.organization.*;
import com.ballot.entity.MembershipStatus;
import com.ballot.entity.User;
import com.ballot.service.MembershipService;
import com.ballot.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management and membership")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final MembershipService membershipService;

    @GetMapping
    @Operation(summary = "List all active organizations (paginated, searchable)")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> getAllOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.getAllOrganizations(page, size, search)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get organizations the current user belongs to")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> getMyOrganizations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.getMyOrganizations(user, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganizationById(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getOrganizationById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new organization (authenticated user becomes OWNER)")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) throws IOException {
        OrganizationResponse response = organizationService.createOrganization(
                request, user, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Organization created successfully.", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization details (admin/owner only)")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.updateOrganization(id, request, user)));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload organization logo to Cloudinary (owner only)")
    public ResponseEntity<ApiResponse<String>> uploadLogo(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        String logoUrl = organizationService.uploadLogo(id, file, user);
        return ResponseEntity.ok(ApiResponse.success("Logo uploaded.", logoUrl));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete organization (soft-delete, owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        organizationService.deleteOrganization(id, user);
        return ResponseEntity.ok(ApiResponse.success("Organization deleted."));
    }

    @PostMapping("/join")
    @Operation(summary = "Join or request to join an organization")
    public ResponseEntity<ApiResponse<String>> joinOrganization(
            @Valid @RequestBody JoinOrganizationRequest request,
            @AuthenticationPrincipal User user) {
        MembershipStatus status = organizationService.joinOrganization(request, user);
        String message = status == MembershipStatus.ACTIVE
                ? "You have successfully joined the organization."
                : "Your join request has been submitted and is pending approval.";
        return ResponseEntity.ok(ApiResponse.success(message, status.name()));
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave an organization")
    public ResponseEntity<ApiResponse<Void>> leaveOrganization(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        organizationService.leaveOrganization(id, user);
        return ResponseEntity.ok(ApiResponse.success("You have left the organization."));
    }

    // ===== Membership sub-resources =====

    @GetMapping("/{id}/members")
    @Operation(summary = "Get active members of an organization")
    public ResponseEntity<ApiResponse<PageResponse<MembershipResponse>>> getMembers(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getMembers(id, page, size)));
    }

    @GetMapping("/{id}/members/pending")
    @Operation(summary = "Get pending join requests (admin/owner only)")
    public ResponseEntity<ApiResponse<PageResponse<MembershipResponse>>> getPendingRequests(
            @PathVariable String id,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipService.getPendingRequests(id, user, page, size)));
    }

    @PostMapping("/{id}/members/requests/{membershipId}/approve")
    @Operation(summary = "Approve a pending join request (admin/owner only)")
    public ResponseEntity<ApiResponse<MembershipResponse>> approveMembership(
            @PathVariable String id,
            @PathVariable String membershipId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipService.approveMembership(id, membershipId, user)));
    }

    @PostMapping("/{id}/members/requests/{membershipId}/reject")
    @Operation(summary = "Reject a pending join request (admin/owner only)")
    public ResponseEntity<ApiResponse<MembershipResponse>> rejectMembership(
            @PathVariable String id,
            @PathVariable String membershipId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipService.rejectMembership(id, membershipId, user)));
    }

    @PutMapping("/{id}/members/{userId}/role")
    @Operation(summary = "Update a member's role (owner only)")
    public ResponseEntity<ApiResponse<MembershipResponse>> updateMemberRole(
            @PathVariable String id,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipService.updateMemberRole(id, userId, request, user,
                        httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"))));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member from the organization (admin/owner only)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String id,
            @PathVariable String userId,
            @AuthenticationPrincipal User user) {
        membershipService.removeMember(id, userId, user);
        return ResponseEntity.ok(ApiResponse.success("Member removed from organization."));
    }
}
