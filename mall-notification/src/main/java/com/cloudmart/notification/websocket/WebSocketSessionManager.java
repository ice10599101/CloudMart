package com.cloudmart.notification.websocket;

import com.cloudmart.notification.dto.NotificationDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerSession(Long userId, WebSocketSession session) {
        WebSocketSession existing = sessions.put(userId, session);
        if (existing != null && existing.isOpen()) {
            try {
                existing.close();
            } catch (IOException e) {
                log.warn("Failed to close existing session for userId={}", userId, e);
            }
        }
        log.info("WebSocket session registered for userId={}", userId);
    }

    public void removeSession(Long userId) {
        sessions.remove(userId);
        log.info("WebSocket session removed for userId={}", userId);
    }

    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public void sendMessageToUser(Long userId, NotificationDTO notification) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            if (session != null) {
                sessions.remove(userId);
            }
            log.debug("User {} is offline, notification will be persisted", userId);
            return;
        }
        try {
            String message = objectMapper.writeValueAsString(notification);
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (JacksonException e) {
            log.error("Failed to serialize notification for userId={}", userId, e);
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to userId={}, removing stale session", userId, e);
            sessions.remove(userId);
        }
    }

    public void sendRawMessage(Long userId, String rawJson) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            if (session != null) {
                sessions.remove(userId);
            }
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(rawJson));
            }
        } catch (IOException e) {
            log.error("Failed to send raw WebSocket message to userId={}, removing stale session", userId, e);
            sessions.remove(userId);
        }
    }
}
