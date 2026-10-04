package com.cloudmart.live.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.service.WebrtcSignalTicketService;
import com.cloudmart.live.service.WebrtcSignalTicketService.TicketIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebrtcServiceImpl 单元测试（T08 房间级授权）")
class WebrtcServiceImplTest {

    private static final Long ROOM_ID = 1L;
    private static final Long HOST_USER_ID = 100L;
    private static final Long VIEWER_A_ID = 200L;
    private static final Long VIEWER_B_ID = 201L;
    private static final String HOST_SESSION = "host-session";
    private static final String VIEWER_A_SESSION = "viewer-a-session";
    private static final String VIEWER_B_SESSION = "viewer-b-session";
    private static final String HOST_TICKET = "ticket-host";
    private static final String VIEWER_A_TICKET = "ticket-viewer-a";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private WebrtcSignalTicketService ticketService;

    private WebrtcServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WebrtcServiceImpl(redisTemplate, ticketService);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(ticketService.validate(HOST_TICKET, ROOM_ID)).thenReturn(Optional.of(
                new TicketIdentity(HOST_USER_ID, ROOM_ID, "HOST", HOST_SESSION)));
        lenient().when(ticketService.validate(VIEWER_A_TICKET, ROOM_ID)).thenReturn(Optional.of(
                new TicketIdentity(VIEWER_A_ID, ROOM_ID, "VIEWER", VIEWER_A_SESSION)));
    }

    @Nested
    @DisplayName("publishSignal - 类型与角色强约束")
    class PublishSignalTest {

        @Test
        @DisplayName("HOST 发布 OFFER：写共享键，且先删旧键（新会话替换旧 OFFER）")
        void hostOffer_replacesSharedKey() {
            service.publishSignal(new WebrtcSignalRequest(ROOM_ID, HOST_TICKET, "OFFER", "sdp-offer"));

            verify(redisTemplate).delete("live:webrtc:signal:1:HOST");
            verify(listOperations).rightPush(eq("live:webrtc:signal:1:HOST"), eq("OFFER|sdp-offer"));
            verify(redisTemplate).expire(eq("live:webrtc:signal:1:HOST"), anyLong(), any());
        }

        @Test
        @DisplayName("VIEWER 冒充发布 OFFER：拒绝（SIGNAL_TYPE_NOT_ALLOWED）")
        void viewerCannotPublishOffer() {
            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(ROOM_ID, VIEWER_A_TICKET, "OFFER", "evil-sdp")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_TYPE_NOT_ALLOWED"));
            verify(listOperations, never()).rightPush(anyString(), anyString());
        }

        @Test
        @DisplayName("HOST 冒充提交 ANSWER：拒绝")
        void hostCannotPublishAnswer() {
            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(ROOM_ID, HOST_TICKET, "ANSWER", "sdp-answer")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_TYPE_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("VIEWER 提交 ANSWER：写自身会话键，他人会话不可及")
        void viewerAnswer_sessionScoped() {
            service.publishSignal(new WebrtcSignalRequest(ROOM_ID, VIEWER_A_TICKET, "ANSWER", "sdp-a"));

            verify(listOperations).rightPush(
                    eq("live:webrtc:signal:1:VIEWER:" + VIEWER_A_SESSION), eq("ANSWER|sdp-a"));
            verify(listOperations, never()).rightPush(
                    startsWith("live:webrtc:signal:1:VIEWER:" + VIEWER_B_SESSION), anyString());
        }

        @Test
        @DisplayName("票据无效/过期：SIGNAL_TICKET_INVALID，不落任何键")
        void invalidTicket_rejected() {
            when(ticketService.validate("bad-ticket", ROOM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(ROOM_ID, "bad-ticket", "OFFER", "sdp")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_TICKET_INVALID"));
            verify(listOperations, never()).rightPush(anyString(), anyString());
        }

        @Test
        @DisplayName("票据跨房间使用：拒绝")
        void crossRoomTicket_rejected() {
            when(ticketService.validate(HOST_TICKET, 999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(999L, HOST_TICKET, "OFFER", "sdp")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("SDP 正文超 64KB：SIGNAL_PAYLOAD_TOO_LARGE")
        void oversizedPayload_rejected() {
            String hugePayload = "x".repeat(65_537);

            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(ROOM_ID, HOST_TICKET, "OFFER", hugePayload)))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_PAYLOAD_TOO_LARGE"));
        }

        @Test
        @DisplayName("发布频率超限：SIGNAL_RATE_LIMITED")
        void rateLimitExceeded_rejected() {
            when(valueOperations.increment(anyString())).thenReturn(121L);

            assertThatThrownBy(() -> service.publishSignal(
                    new WebrtcSignalRequest(ROOM_ID, HOST_TICKET, "OFFER", "sdp")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_RATE_LIMITED"));
        }
    }

    @Nested
    @DisplayName("getSignals - 观众会话隔离")
    class GetSignalsTest {

        @Test
        @DisplayName("观众读 HOST：返回共享 OFFER")
        void viewerReadsHostOffer() {
            when(listOperations.range("live:webrtc:signal:1:HOST", 0, -1))
                    .thenReturn(List.of("OFFER|sdp-offer"));

            var signals = service.getSignals(ROOM_ID, "HOST", VIEWER_A_TICKET);

            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).type()).isEqualTo("OFFER");
        }

        @Test
        @DisplayName("观众读 VIEWER：服务端强制只返回自身会话（忽略任意会话枚举）")
        void viewerReadsOwnSessionOnly() {
            when(listOperations.range("live:webrtc:signal:1:VIEWER:" + VIEWER_A_SESSION, 0, -1))
                    .thenReturn(List.of("ANSWER|own"));

            var signals = service.getSignals(ROOM_ID, "VIEWER", VIEWER_A_TICKET);

            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).payload()).isEqualTo("own");
            verify(listOperations, never()).range(
                    eq("live:webrtc:signal:1:VIEWER:" + VIEWER_B_SESSION), anyLong(), anyLong());
        }

        @Test
        @DisplayName("非法目标角色：SIGNAL_ROLE_INVALID")
        void invalidTargetRole_rejected() {
            assertThatThrownBy(() -> service.getSignals(ROOM_ID, "MODERATOR", VIEWER_A_TICKET))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_ROLE_INVALID"));
        }
    }

    @Nested
    @DisplayName("publishIceCandidate / getIceCandidates")
    class IceTest {

        @Test
        @DisplayName("观众 ICE 写自身会话键")
        void viewerIce_sessionScoped() {
            service.publishIceCandidate(
                    new WebrtcSignalRequest(ROOM_ID, VIEWER_A_TICKET, "ICE_CANDIDATE", "{\"candidate\":1}"));

            verify(listOperations).rightPush(
                    eq("live:webrtc:ice:1:VIEWER:" + VIEWER_A_SESSION), eq("{\"candidate\":1}"));
        }

        @Test
        @DisplayName("ICE 通道拒绝非 ICE_CANDIDATE 类型")
        void iceChannel_rejectsNonIceType() {
            assertThatThrownBy(() -> service.publishIceCandidate(
                    new WebrtcSignalRequest(ROOM_ID, HOST_TICKET, "OFFER", "sdp")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_TYPE_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("观众读自身会话 ICE：仅返回自身候选")
        void viewerIce_ownSessionOnly() {
            when(listOperations.range("live:webrtc:ice:1:VIEWER:" + VIEWER_A_SESSION, 0, -1))
                    .thenReturn(List.of("{\"candidate\":1}"));

            var candidates = service.getIceCandidates(ROOM_ID, "VIEWER", VIEWER_A_TICKET);

            assertThat(candidates).containsExactly("{\"candidate\":1}");
        }
    }

    @Nested
    @DisplayName("clearSignals - 仅房主")
    class ClearSignalsTest {

        @Test
        @DisplayName("观众清信令：SIGNAL_CLEAR_FORBIDDEN，共享键不被删除")
        void viewerCannotClear() {
            assertThatThrownBy(() -> service.clearSignals(ROOM_ID, VIEWER_A_TICKET))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getCode()).isEqualTo("SIGNAL_CLEAR_FORBIDDEN"));
            verify(redisTemplate, never()).delete((java.util.Collection<String>) any());
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("房主清信令：共享键与全部观众会话键删除")
        void hostClearsAll() {
            org.springframework.data.redis.core.Cursor<String> cursor =
                    org.mockito.Mockito.mock(org.springframework.data.redis.core.Cursor.class);
            org.mockito.Mockito.when(cursor.hasNext()).thenReturn(true, false);
            org.mockito.Mockito.when(cursor.next())
                    .thenReturn("live:webrtc:signal:1:VIEWER:" + VIEWER_A_SESSION, null);
            when(redisTemplate.scan(any())).thenReturn(cursor);

            service.clearSignals(ROOM_ID, HOST_TICKET);

            verify(redisTemplate).delete("live:webrtc:signal:1:HOST");
            verify(redisTemplate).delete("live:webrtc:ice:1:HOST");
            verify(redisTemplate).delete(List.of("live:webrtc:signal:1:VIEWER:" + VIEWER_A_SESSION));
        }
    }

    @Nested
    @DisplayName("issueSignalTicket - 票据签发委托")
    class IssueTicketTest {

        @Test
        @DisplayName("签发委托给票据服务")
        void delegatesToTicketService() {
            var issued = new WebrtcSignalTicketService.IssuedTicket(
                    "t", "HOST", "s", java.time.Instant.now());
            when(ticketService.issue(HOST_USER_ID, ROOM_ID)).thenReturn(issued);

            assertThat(service.issueSignalTicket(HOST_USER_ID, ROOM_ID)).isSameAs(issued);
        }
    }
}
