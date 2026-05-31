package com.ballot.dto.chat;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {
    private final String id;
    private final String organizationId;
    private final String senderId;
    private final String senderFirstName;
    private final String senderLastName;
    private final String content;
    private final LocalDateTime createdAt;
}
