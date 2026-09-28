package com.cloudmart.live.websocket;

import com.cloudmart.live.service.LiveWsTicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 弹幕 WS 握手拦截器（LIVE-01）：校验一次性票据并把服务端签发的身份写入会话。
 *
 * <p>携带票据 → 消费成功（一次性、30 秒内、绑定房间）后身份写入 {@code attributes}
 * （userId/nickname），处理器据此建立发消息身份；无票据 → 匿名观看连接（可订阅，
 * 不可发消息）；票据无效/过期/重放/跨房间 → 拒绝握手（401）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveWsHandshakeInterceptor implements HandshakeInterceptor {

    /** 会话属性键：已认证的用户 ID（匿名连接不存在该键） */
    public static final String ATTR_USER_ID = "wsUserId";
    /** 会话属性键：签发时绑定的昵称 */
    public static final String ATTR_NICKNAME = "wsNickname";

    private final LiveWsTicketService liveWsTicketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long roomId = null;
        String ticket = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            roomId = parseLong(httpRequest.getParameter("roomId"));
            ticket = httpRequest.getParameter("ticket");
        }

        if (ticket == null || ticket.isBlank()) {
            // 匿名观看：公共房间可订阅，写消息在处理器层被拒
            return true;
        }

        final Long ticketRoomId = roomId;
        return liveWsTicketService.consume(ticket, ticketRoomId)
                .map(identity -> {
                    attributes.put(ATTR_USER_ID, identity.userId());
                    attributes.put(ATTR_NICKNAME, identity.nickname());
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("[LIVE01] WS 握手票据无效或已消费，拒绝连接 roomId={}", ticketRoomId);
                    response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return false;
                });
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
