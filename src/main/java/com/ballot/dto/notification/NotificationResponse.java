package com.ballot.dto.notification;

import com.ballot.entity.NotificationStatus;
import com.ballot.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private final String id;
    private final String title;
    private final String message;
    private final NotificationType type;
    private final NotificationStatus status;
    private final String actionUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;
}
