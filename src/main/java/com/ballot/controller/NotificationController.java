package com.ballot.controller;

import com.ballot.dto.common.ApiResponse;
import com.ballot.dto.common.PageResponse;
import com.ballot.dto.notification.NotificationResponse;
import com.ballot.entity.Notification;
import com.ballot.entity.NotificationStatus;
import com.ballot.entity.User;
import com.ballot.exception.ResourceNotFoundException;
import com.ballot.exception.UnauthorizedException;
import com.ballot.repository.NotificationRepository;
import com.ballot.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final com.ballot.service.SseService sseService;

    @GetMapping("/stream")
    @Operation(summary = "Subscribe to real-time notification stream via SSE")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@AuthenticationPrincipal User user) {
        return sseService.subscribe(user.getId());
    }

    @GetMapping
    @Operation(summary = "Get paginated notifications for the current user (sortable)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        // PAGINATION + SORTING applied together via PageUtil
        Page<Notification> notifications = notificationRepository
                .findAllByUserId(user.getId(), PageUtil.of(page, size, sortBy, direction));
        PageResponse<NotificationResponse> response = PageUtil.toPageResponse(
                notifications.map(this::toResponse));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@AuthenticationPrincipal User user) {
        long count = notificationRepository.countByUserIdAndStatus(user.getId(), NotificationStatus.UNREAD);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a specific notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Cannot access another user's notification.");
        }
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);
        return ResponseEntity.ok(ApiResponse.success(toResponse(notification)));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(@AuthenticationPrincipal User user) {
        int count = notificationRepository.markAllAsReadByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Marked " + count + " notifications as read.", count));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .status(n.getStatus())
                .actionUrl(n.getActionUrl())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}
