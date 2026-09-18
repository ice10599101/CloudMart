package com.cloudmart.live.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 弹幕 WebSocket 配置。
 *
 * <p>处理器为 Spring Bean（{@link LiveDanmakuHandler}）：内部礼物广播接口
 * 与 WS 会话共用同一连接表，广播可达房间内全部在线观众。</p>
 */
@Configuration
@EnableWebSocket
public class LiveWebSocketConfig implements WebSocketConfigurer {

    private final LiveDanmakuHandler liveDanmakuHandler;

    public LiveWebSocketConfig(LiveDanmakuHandler liveDanmakuHandler) {
        this.liveDanmakuHandler = liveDanmakuHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveDanmakuHandler, "/ws/live/danmaku")
            .setAllowedOriginPatterns("*");
    }
}
