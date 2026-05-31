package com.ballot.controller;

import com.ballot.dto.common.ApiResponse;
import com.ballot.dto.common.PageResponse;
import com.ballot.dto.chat.ChatMessageResponse;
import com.ballot.dto.chat.SendChatMessageRequest;
import com.ballot.entity.User;
import com.ballot.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getMessages(
            @PathVariable String orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User userDetails) {

        Page<ChatMessageResponse> messages = chatService.getMessages(orgId, userDetails.getId(), page, size);
        PageResponse<ChatMessageResponse> pageResponse = PageResponse.<ChatMessageResponse>builder()
                .content(messages.getContent())
                .page(messages.getNumber())
                .size(messages.getSize())
                .totalElements(messages.getTotalElements())
                .totalPages(messages.getTotalPages())
                .first(messages.isFirst())
                .last(messages.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Chat messages fetched", pageResponse));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable String orgId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal User userDetails) {

        ChatMessageResponse message = chatService.sendMessage(orgId, userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Message sent", message));
    }
}
