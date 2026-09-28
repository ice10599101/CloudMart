package com.cloudmart.live.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 弹幕 WebSocket 配置（LIVE-01）。
 *
 * <p>处理器为 Spring Bean（{@link LiveDanmakuHandler}）：内部礼物广播接口
 * 与 WS 会话共用同一连接表，广播可达房间内全部在线观众。握手经
 * {@link LiveWsHandshakeInterceptor} 校验一次性票据并绑定服务端身份。</p>
 */
@Configuration
@EnableWebSocket
public class LiveWebSocketConfig implements WebSocketConfigurer {

    private final LiveDanmakuHandler liveDanmakuHandler;
    private final LiveWsHandshakeInterceptor liveWsHandshakeInterceptor;

    public LiveWebSocketConfig(LiveDanmakuHandler liveDanmakuHandler,
                               LiveWsHandshakeInterceptor liveWsHandshakeInterceptor) {
        this.liveDanmakuHandler = liveDanmakuHandler;
        this.liveWsHandshakeInterceptor = liveWsHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveDanmakuHandler, "/ws/live/danmaku")
            .addInterceptors(liveWsHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}
