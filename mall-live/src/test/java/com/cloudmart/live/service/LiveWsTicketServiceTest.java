package com.cloudmart.live.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LIVE-01：直播 WS 一次性票据——签发绑定身份与房间、消费原子一次性、
 * 重放/跨房间拒绝、Redis 故障 fail-closed。
 */
@DisplayName("LiveWsTicketService WS 一次性票据")
class LiveWsTicketServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ROOM_ID = 7L;

    private LiveWsTicketService service;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LiveWsTicketService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("签发写入 30 秒 TTL 的票据并返回 wsPath")
    void issue_storesTicketWithTtl() {
        LiveWsTicketService.IssuedTicket issued = service.issue(USER_ID, "测试用户", ROOM_ID);

        assertThat(issued.ticket()).isNotBlank();
        assertThat(issued.wsPath()).contains("/ws/live/danmaku").contains("roomId=7").contains(issued.ticket());
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("live:ws_ticket:" + issued.ticket()), valueCaptor.capture(),
                eq(Duration.ofSeconds(30)));
        assertThat(valueCaptor.getValue()).contains("\"userId\":" + USER_ID).contains("\"roomId\":" + ROOM_ID);
    }

    @Test
    @DisplayName("消费成功返回绑定身份且票据只能消费一次（重放拒绝）")
    void consume_onceOnly() {
        LiveWsTicketService.IssuedTicket issued = service.issue(USER_ID, "测试用户", ROOM_ID);
        String stored = objectMapper.writeValueAsString(
                new LiveWsTicketService.TicketIdentity(USER_ID, "测试用户", ROOM_ID));
        when(valueOperations.get("live:ws_ticket:" + issued.ticket())).thenReturn(stored);
        when(redisTemplate.delete("live:ws_ticket:" + issued.ticket())).thenReturn(true);

        Optional<LiveWsTicketService.TicketIdentity> consumed = service.consume(issued.ticket(), ROOM_ID);

        assertThat(consumed).isPresent();
        assertThat(consumed.get().userId()).isEqualTo(USER_ID);
        assertThat(consumed.get().roomId()).isEqualTo(ROOM_ID);

        // 重放：票据已删除（DEL 返回 0）→ 拒绝
        when(redisTemplate.delete("live:ws_ticket:" + issued.ticket())).thenReturn(false);
        assertThat(service.consume(issued.ticket(), ROOM_ID)).isEmpty();
    }

    @Test
    @DisplayName("跨房间使用票据被拒绝（票据与房间绑定）")
    void consume_wrongRoom_rejected() {
        LiveWsTicketService.IssuedTicket issued = service.issue(USER_ID, "测试用户", ROOM_ID);
        String stored = objectMapper.writeValueAsString(
                new LiveWsTicketService.TicketIdentity(USER_ID, "测试用户", ROOM_ID));
        when(valueOperations.get("live:ws_ticket:" + issued.ticket())).thenReturn(stored);

        assertThat(service.consume(issued.ticket(), 999L)).isEmpty();
        // 未消费原票据
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("未知票据拒绝且不触碰删除")
    void consume_unknownTicket_rejected() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(service.consume("no-such-ticket", ROOM_ID)).isEmpty();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Redis 故障签发 fail-closed（抛异常，不发票据）")
    void issue_redisFailure_failsClosed() {
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));

        assertThatThrownBy(() -> service.issue(USER_ID, "测试用户", ROOM_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Redis 故障消费 fail-closed（拒绝握手而非放行）")
    void consume_redisFailure_failsClosed() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.consume("any-ticket", ROOM_ID)).isEmpty();
        verify(redisTemplate, times(0)).delete(anyString());
    }
}
