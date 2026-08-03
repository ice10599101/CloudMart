package com.cloudmart.live.netty;

import com.cloudmart.live.dto.DanmakuMessage;
import tools.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;

/**
 * Netty WebSocket 帧处理器。
 * 处理连接建立/断开、弹幕消息转发、空闲连接清理。
 */
public class DanmakuFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(DanmakuFrameHandler.class);

    private final DanmakuChannelHandler channelHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DanmakuFrameHandler(DanmakuChannelHandler channelHandler) {
        this.channelHandler = channelHandler;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshake) {
            // WebSocket 握手完成，解析 roomId 和 userId
            URI uri = URI.create(handshake.requestUri());
            String query = uri.getQuery();
            Long roomId = extractParam(query, "roomId");
            Long userId = extractParam(query, "userId");

            if (roomId == null) {
                ctx.close();
                return;
            }

            channelHandler.joinRoom(ctx.channel(), roomId, userId != null ? userId : 0L);
        } else if (evt instanceof IdleStateEvent) {
            // 空闲超时，关闭僵尸连接
            log.debug("Closing idle connection: {}", ctx.channel().id());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        channelHandler.handleDanmaku(ctx.channel(), frame.text());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelHandler.leaveRoom(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty channel exception: {}", cause.getMessage());
        ctx.close();
    }

    private Long extractParam(String query, String paramName) {
        if (query == null || query.isBlank()) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (paramName.equals(kv[0]) && kv.length == 2) {
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
