package com.cloudmart.notification.websocket;

import com.cloudmart.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final String PING_MESSAGE = "ping";
    private static final String PONG_MESSAGE = "pong";

    private final WebSocketSessionManager sessionManager;
    private final NotificationService notificationService;

    public NotificationWebSocketHandler(WebSocketSessionManager sessionManager,
                                        NotificationService notificationService) {
        this.sessionManager = sessionManager;
        this.notificationService = notificationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessionManager.registerSession(userId, session);

        var unread = notificationService.getUnreadCount(userId);
        String unreadJson = "{\"type\":\"UNREAD_COUNT\",\"count\":" + unread.count() + "}";
        synchronized (session) {
            session.sendMessage(new TextMessage(unreadJson));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (PING_MESSAGE.equals(payload)) {
            synchronized (session) {
                session.sendMessage(new TextMessage(PONG_MESSAGE));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessionManager.removeSession(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessionManager.removeSession(userId);
        }
        log.warn("WebSocket transport error for userId={}", userId, exception);
    }

    private Long extractUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long l) {
            return l;
        }
        return null;
    }
}
