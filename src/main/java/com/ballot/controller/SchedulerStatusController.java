package com.ballot.controller;

import com.ballot.dto.common.ApiResponse;
import com.ballot.service.DynamicBallotScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Exposes the live state of all dynamically scheduled ballot tasks.
 * Used by the frontend "Scheduled Timeline" panel on ballot detail pages.
 *
 * GET /api/v1/scheduler/status          → all pending tasks across all ballots
 * GET /api/v1/scheduler/status/{ballotId} → pending tasks for one ballot
 */
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "Live status of dynamically scheduled ballot tasks")
public class SchedulerStatusController {

    private final DynamicBallotScheduler dynamicScheduler;

    @GetMapping("/status")
    @Operation(summary = "List all pending scheduled tasks (admin/owner)")
    public ResponseEntity<ApiResponse<List<DynamicBallotScheduler.ScheduledTaskInfo>>> getAllPendingTasks() {
        List<DynamicBallotScheduler.ScheduledTaskInfo> tasks = dynamicScheduler.getPendingTasks()
                .stream()
                .filter(t -> t.scheduledAt().isAfter(LocalDateTime.now()))
                .sorted((a, b) -> a.scheduledAt().compareTo(b.scheduledAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/status/{ballotId}")
    @Operation(summary = "List pending scheduled tasks for a specific ballot")
    public ResponseEntity<ApiResponse<List<DynamicBallotScheduler.ScheduledTaskInfo>>> getTasksForBallot(
            @PathVariable String ballotId) {
        List<DynamicBallotScheduler.ScheduledTaskInfo> tasks = dynamicScheduler.getPendingTasks()
                .stream()
                .filter(t -> t.ballotId().equals(ballotId) && t.scheduledAt().isAfter(LocalDateTime.now()))
                .sorted((a, b) -> a.scheduledAt().compareTo(b.scheduledAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }
}
