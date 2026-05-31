package com.ballot.service;

import com.ballot.dto.chat.ChatMessageResponse;
import com.ballot.dto.chat.SendChatMessageRequest;
import com.ballot.entity.*;
import com.ballot.exception.ResourceNotFoundException;
import com.ballot.exception.UnauthorizedException;
import com.ballot.repository.ChatMessageRepository;
import com.ballot.repository.MembershipRepository;
import com.ballot.repository.OrganizationRepository;
import com.ballot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final SseService sseService;

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getMessages(String orgId, String userId, int page, int size) {
        validateMembership(orgId, userId);
        Pageable pageable = PageRequest.of(page, size);
        return chatMessageRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public ChatMessageResponse sendMessage(String orgId, String userId, SendChatMessageRequest request) {
        validateMembership(orgId, userId);

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ChatMessage message = ChatMessage.builder()
                .organization(org)
                .user(user)
                .content(request.getContent())
                .createdAt(java.time.LocalDateTime.now())
                .build();

        message = chatMessageRepository.save(message);
        ChatMessageResponse response = mapToResponse(message);

        // Broadcast via SSE to all active members
        List<Membership> activeMembers = membershipRepository.findAllByOrganizationIdAndStatus(orgId, MembershipStatus.ACTIVE);
        for (Membership member : activeMembers) {
            sseService.sendChatMessage(member.getUser().getId(), response);
        }

        return response;
    }

    private void validateMembership(String orgId, String userId) {
        boolean isMember = membershipRepository.existsByUserIdAndOrganizationIdAndStatus(
                userId, orgId, MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new UnauthorizedException("You are not an active member of this organization.");
        }
    }

    private ChatMessageResponse mapToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .organizationId(message.getOrganization().getId())
                .senderId(message.getUser().getId())
                .senderFirstName(message.getUser().getFirstName())
                .senderLastName(message.getUser().getLastName())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
