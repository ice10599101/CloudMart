package com.cloudmart.live.websocket;

import com.cloudmart.live.dto.DanmakuMessage;
import com.cloudmart.live.repository.LiveDanmakuMapper;
import com.cloudmart.live.service.LiveRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LIVE-01：弹幕处理器身份绑定——发消息必须使用握手时绑定的服务端身份，
 * 载荷中的 userId/nickname 一律忽略；匿名连接发消息被关闭（只可观看）。
 */
@DisplayName("LiveDanmakuHandler 会话身份绑定")
class LiveDanmakuHandlerTest {

    private LiveDanmakuHandler handler;
    private LiveDanmakuMapper danmakuMapper;
    private LiveRoomService liveRoomService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        danmakuMapper = mock(LiveDanmakuMapper.class);
        liveRoomService = mock(LiveRoomService.class);
        handler = new LiveDanmakuHandler(danmakuMapper, liveRoomService, objectMapper);
    }

    private WebSocketSession session(Long authenticatedUserId, String nickname) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/live/danmaku?roomId=7"));
        when(session.getId()).thenReturn("session-1");
        Map<String, Object> attributes = new HashMap<>();
        if (authenticatedUserId != null) {
            attributes.put(LiveWsHandshakeInterceptor.ATTR_USER_ID, authenticatedUserId);
            attributes.put(LiveWsHandshakeInterceptor.ATTR_NICKNAME, nickname);
        }
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("伪造身份被忽略：广播与入库使用会话绑定的服务端身份")
    void handleTextMessage_payloadIdentityIgnored() throws Exception {
        WebSocketSession session = session(42L, "真实昵称");
        handler.afterConnectionEstablished(session);

        // 客户端尝试冒充 userId=999 / 昵称"假人"
        DanmakuMessage spoofed = new DanmakuMessage(7L, 999L, "假人", "你好直播间", 0L);
        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(spoofed)));

        ArgumentCaptor<DanmakuMessage> broadcastCaptor = ArgumentCaptor.forClass(DanmakuMessage.class);
        // 广播走 session.sendMessage（同房间只有发送者自己）
        verify(session).sendMessage(any(TextMessage.class));
        ArgumentCaptor<com.cloudmart.live.entity.LiveDanmaku> persistCaptor =
                ArgumentCaptor.forClass(com.cloudmart.live.entity.LiveDanmaku.class);
        verify(danmakuMapper).insert(persistCaptor.capture());
        assertThat(persistCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(persistCaptor.getValue().getNickname()).isEqualTo("真实昵称");
    }

    @Test
    @DisplayName("匿名连接发弹幕：连接被关闭（POLICY_VIOLATION），不广播不入库")
    void handleTextMessage_anonymous_closed() throws Exception {
        WebSocketSession session = session(null, null);
        handler.afterConnectionEstablished(session);

        DanmakuMessage message = new DanmakuMessage(7L, 999L, "游客", "刷屏", 0L);
        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(message)));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(danmakuMapper, never()).insert(any(com.cloudmart.live.entity.LiveDanmaku.class));
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("无 roomId 的连接在建立时即被关闭")
    void afterConnectionEstablished_missingRoomId_closed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/live/danmaku"));
        when(session.getId()).thenReturn("session-2");

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verify(liveRoomService, never()).incrementViewer(anyLong());
    }
}
