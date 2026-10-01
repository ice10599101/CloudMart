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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** N01：userId → 该用户全部活跃会话（多设备并存；QA40：旧连接关闭不误删新连接） */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    /** session → 所属用户（关闭时条件删除对应会话） */
    private final Map<WebSocketSession, Long> sessionOwners = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerSession(Long userId, WebSocketSession session) {
        // N01：多设备并存——不关闭旧连接（旧实现 put+close 旧连接，旧连接 onClose 又误删新连接）
        sessionOwners.put(session, userId);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket session registered for userId={}, activeSessions={}",
                userId, sessions.get(userId).size());
    }

    /** N01：按具体 session 条件删除——只移除关闭的那条，不影响同用户其他设备 */
    public void removeSession(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId, userSessions);
            }
        }
        sessionOwners.remove(session);
        log.info("WebSocket session removed for userId={}", userId);
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return false;
        }
        userSessions.removeIf(s -> !s.isOpen());
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            return false;
        }
        return true;
    }

    public void sendMessageToUser(Long userId, NotificationDTO notification) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            log.debug("User {} is offline, notification will be persisted", userId);
            return;
        }
        String message;
        try {
            message = objectMapper.writeValueAsString(notification);
        } catch (JacksonException e) {
            log.error("Failed to serialize notification for userId={}", userId, e);
            return;
        }
        for (WebSocketSession session : userSessions.toArray(new WebSocketSession[0])) {
            sendToSession(userId, session, message);
        }
    }

    public void sendRawMessage(Long userId, String rawJson) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : userSessions.toArray(new WebSocketSession[0])) {
            sendToSession(userId, session, rawJson);
        }
    }

    /** N01：单会话发送 + 失败条件清理（只清坏会话本身） */
    private void sendToSession(Long userId, WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId, userSessions);
                }
            }
            sessionOwners.remove(session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to userId={}, removing stale session", userId, e);
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId, userSessions);
                }
            }
            sessionOwners.remove(session);
        }
    }
}
