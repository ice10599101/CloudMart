package com.cloudmart.live.websocket;

import com.cloudmart.live.repository.LiveDanmakuMapper;
import com.cloudmart.live.service.LiveRoomService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LiveWebSocketConfig implements WebSocketConfigurer {

    private final LiveDanmakuMapper danmakuMapper;
    private final LiveRoomService liveRoomService;
    private final ObjectMapper objectMapper;

    public LiveWebSocketConfig(LiveDanmakuMapper danmakuMapper,
                                LiveRoomService liveRoomService,
                                ObjectMapper objectMapper) {
        this.danmakuMapper = danmakuMapper;
        this.liveRoomService = liveRoomService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new LiveDanmakuHandler(danmakuMapper, liveRoomService, objectMapper), "/ws/live/danmaku")
            .setAllowedOriginPatterns("*");
    }
}
