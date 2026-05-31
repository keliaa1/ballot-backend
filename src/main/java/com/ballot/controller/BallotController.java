package com.ballot.controller;

import com.ballot.dto.ballot.*;
import com.ballot.dto.common.ApiResponse;
import com.ballot.dto.common.PageResponse;
import com.ballot.dto.vote.CastVoteRequest;
import com.ballot.dto.vote.VoteResponse;
import com.ballot.entity.User;
import com.ballot.service.BallotService;
import com.ballot.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ballots")
@RequiredArgsConstructor
@Tag(name = "Ballots", description = "Ballot creation, management, and voting")
public class BallotController {

    private final BallotService ballotService;
    private final VoteService voteService;

    @GetMapping("/active")
    @Operation(summary = "Get all active ballots for the authenticated user's organizations")
    public ResponseEntity<ApiResponse<PageResponse<BallotResponse>>> getActiveBallotsForMe(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ballotService.getVisibleBallotsForUser(user, page, size)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get all ballots created by the authenticated user")
    public ResponseEntity<ApiResponse<PageResponse<BallotResponse>>> getMyBallots(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ballotService.getMyBallots(user, page, size)));
    }

    @GetMapping("/org/{orgId}")
    @Operation(summary = "Get all ballots for a specific organization (members only)")
    public ResponseEntity<ApiResponse<PageResponse<BallotResponse>>> getBallotsByOrg(
            @PathVariable String orgId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ballotService.getBallotsByOrg(orgId, user, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ballot by ID")
    public ResponseEntity<ApiResponse<BallotResponse>> getBallotById(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(ballotService.getBallotById(id, user)));
    }

    @PostMapping
    @Operation(summary = "Create a new ballot (admin/owner only)")
    public ResponseEntity<ApiResponse<BallotResponse>> createBallot(
            @Valid @RequestBody CreateBallotRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {
        BallotResponse response = ballotService.createBallot(
                request, user, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ballot created successfully.", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a DRAFT ballot (admin/owner only)")
    public ResponseEntity<ApiResponse<BallotResponse>> updateBallot(
            @PathVariable String id,
            @Valid @RequestBody UpdateBallotRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                ballotService.updateBallot(id, request, user)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT ballot (admin/owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteBallot(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        ballotService.deleteBallot(id, user);
        return ResponseEntity.ok(ApiResponse.success("Ballot deleted."));
    }

    // ===== Voting sub-resources =====

    @PostMapping("/{id}/vote")
    @Operation(summary = "Cast a vote on a ballot")
    public ResponseEntity<ApiResponse<VoteResponse>> castVote(
            @PathVariable String id,
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {
        VoteResponse response = voteService.castVote(
                id, request, user, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vote cast successfully.", response));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Manually close an active ballot and compute results (admin/owner only)")
    public ResponseEntity<ApiResponse<Void>> closeBallot(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        ballotService.manuallyCloseBallot(id, user);
        return ResponseEntity.ok(ApiResponse.success("Ballot closed successfully."));
    }

    @GetMapping("/{id}/my-vote")
    @Operation(summary = "Get your current vote for a ballot")
    public ResponseEntity<ApiResponse<VoteResponse>> getMyVote(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(voteService.getMyVote(id, user)));
    }
}
