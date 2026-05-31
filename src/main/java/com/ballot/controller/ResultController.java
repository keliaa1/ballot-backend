package com.ballot.controller;

import com.ballot.dto.common.ApiResponse;
import com.ballot.dto.result.BallotResultResponse;
import com.ballot.entity.User;
import com.ballot.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
@Tag(name = "Results", description = "Ballot result retrieval (closed ballots only)")
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/{ballotId}")
    @Operation(summary = "Get results for a closed ballot (members only)")
    public ResponseEntity<ApiResponse<BallotResultResponse>> getResults(
            @PathVariable String ballotId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(resultService.getResults(ballotId, user)));
    }
}
