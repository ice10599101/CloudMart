package com.cloudmart.live.websocket;

import com.cloudmart.live.dto.DanmakuMessage;
import com.cloudmart.live.entity.LiveDanmaku;
import com.cloudmart.live.repository.LiveDanmakuMapper;
import com.cloudmart.live.service.LiveRoomService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LiveDanmakuHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveDanmakuHandler.class);
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_ROOM_MESSAGES_PER_SECOND = 100;

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final LiveDanmakuMapper danmakuMapper;
    private final LiveRoomService liveRoomService;
    private final ObjectMapper objectMapper;

    public LiveDanmakuHandler(LiveDanmakuMapper danmakuMapper,
                               LiveRoomService liveRoomService,
                               ObjectMapper objectMapper) {
        this.danmakuMapper = danmakuMapper;
        this.liveRoomService = liveRoomService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long roomId = extractRoomId(session);
        if (roomId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        liveRoomService.incrementViewer(roomId);
        log.debug("WebSocket connected: roomId={}, sessionId={}", roomId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long roomId = extractRoomId(session);
        if (roomId == null) {
            return;
        }

        DanmakuMessage danmaku;
        try {
            danmaku = objectMapper.readValue(message.getPayload(), DanmakuMessage.class);
        } catch (Exception e) {
            log.warn("Invalid danmaku message: {}", e.getMessage());
            return;
        }

        if (danmaku.content() == null || danmaku.content().isBlank()) {
            return;
        }
        if (danmaku.content().length() > MAX_CONTENT_LENGTH) {
            return;
        }

        DanmakuMessage enriched = new DanmakuMessage(
            roomId, danmaku.userId(), danmaku.nickname(),
            sanitizeContent(danmaku.content()),
            System.currentTimeMillis()
        );

        persistDanmaku(enriched);

        broadcastToRoom(roomId, enriched, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long roomId = extractRoomId(session);
        if (roomId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
            liveRoomService.decrementViewer(roomId);
        }
        log.debug("WebSocket disconnected: roomId={}, sessionId={}, status={}", roomId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void broadcastToRoom(Long roomId, DanmakuMessage message, WebSocketSession sender) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Failed to send danmaku to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to broadcast danmaku: {}", e.getMessage());
        }
    }

    private void persistDanmaku(DanmakuMessage message) {
        try {
            LiveDanmaku entity = new LiveDanmaku();
            entity.setRoomId(message.roomId());
            entity.setUserId(message.userId());
            entity.setNickname(message.nickname());
            entity.setContent(message.content());
            danmakuMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to persist danmaku: {}", e.getMessage());
        }
    }

    private String sanitizeContent(String content) {
        return content.replaceAll("<[^>]*>", "")
                      .replaceAll("[\\r\\n]", " ")
                      .trim();
    }

    private Long extractRoomId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if ("roomId".equals(kv[0]) && kv.length == 2) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
