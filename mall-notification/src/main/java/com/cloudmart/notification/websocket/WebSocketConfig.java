package com.cloudmart.notification.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtDecoder jwtDecoder;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}")
    private String allowedOrigins;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                           JwtDecoder jwtDecoder) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .addInterceptors(new JwtHandshakeInterceptor(jwtDecoder));
    }

    private static class JwtHandshakeInterceptor implements HandshakeInterceptor {

        private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
        private final JwtDecoder jwtDecoder;

        JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
            this.jwtDecoder = jwtDecoder;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String token = servletRequest.getServletRequest().getParameter("token");
                if (token == null || token.isBlank()) {
                    log.warn("WebSocket handshake rejected: no token provided");
                    return false;
                }
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    String userId = jwt.getSubject();
                    attributes.put("userId", Long.parseLong(userId));
                    return true;
                } catch (Exception e) {
                    log.warn("WebSocket handshake rejected: invalid token - {}", e.getMessage());
                    return false;
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
