package com.ballot.service;

import com.ballot.dto.notification.NotificationResponse;
import com.ballot.dto.chat.ChatMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        // 1-hour timeout to avoid holding connections indefinitely
        SseEmitter emitter = new SseEmitter(3600000L);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for user {}", userId);
            emitters.remove(userId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for user {}", userId);
            emitters.remove(userId, emitter);
        });
        emitter.onError((e) -> {
            log.debug("SSE error for user {}", userId, e);
            emitters.remove(userId, emitter);
        });

        // Send an initial connected event
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }

        return emitter;
    }

    public void sendNotification(String userId, NotificationResponse notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.error("Error sending SSE to user {}", userId, e);
                emitters.remove(userId, emitter);
            }
        }
    }
    public void sendChatMessage(String userId, ChatMessageResponse message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chat_message")
                        .data(message));
            } catch (IOException e) {
                log.error("Error sending chat SSE to user {}", userId, e);
                emitters.remove(userId, emitter);
            }
        }
    }
}
