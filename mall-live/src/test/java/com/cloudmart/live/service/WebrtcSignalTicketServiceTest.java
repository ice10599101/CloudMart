package com.cloudmart.live.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.repository.LiveRoomMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T08：信令票据——角色由服务端按 anchorUserId 派生、绑定房间与 peerSessionId、
 * 滑动续期、跨房间拒绝、房间非直播拒绝、Redis 故障 fail-closed。
 */
@DisplayName("WebrtcSignalTicketService 信令票据（T08）")
class WebrtcSignalTicketServiceTest {

    private static final Long HOST_USER_ID = 100L;
    private static final Long VIEWER_USER_ID = 200L;
    private static final Long ROOM_ID = 7L;

    private WebrtcSignalTicketService service;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LiveRoomMapper liveRoomMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        liveRoomMapper = mock(LiveRoomMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new WebrtcSignalTicketService(redisTemplate, objectMapper, liveRoomMapper);
    }

    private LiveRoom liveRoom() {
        LiveRoom room = new LiveRoom();
        room.setId(ROOM_ID);
        room.setAnchorUserId(HOST_USER_ID);
        room.setStatus("LIVE");
        return room;
    }

    @Test
    @DisplayName("房主签发：角色 HOST，票据绑定房间与会话")
    void issue_hostRole() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());

        var issued = service.issue(HOST_USER_ID, ROOM_ID);

        assertThat(issued.role()).isEqualTo("HOST");
        assertThat(issued.ticket()).isNotBlank();
        assertThat(issued.peerSessionId()).isNotBlank();
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("live:signal_ticket:" + issued.ticket()), valueCaptor.capture(),
                eq(Duration.ofSeconds(60)));
        assertThat(valueCaptor.getValue())
                .contains("\"userId\":" + HOST_USER_ID)
                .contains("\"roomId\":" + ROOM_ID)
                .contains("\"role\":\"HOST\"");
    }

    @Test
    @DisplayName("普通观众签发：角色 VIEWER（客户端无自报入口）")
    void issue_viewerRole() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());

        var issued = service.issue(VIEWER_USER_ID, ROOM_ID);

        assertThat(issued.role()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("房间不存在/未直播：拒绝签发")
    void issue_roomNotLive_rejected() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());

        assertThatThrownBy(() -> service.issue(VIEWER_USER_ID, 999L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("LIVE_ROOM_NOT_FOUND"));

        LiveRoom ended = liveRoom();
        ended.setStatus("ENDED");
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(ended);
        assertThatThrownBy(() -> service.issue(VIEWER_USER_ID, ROOM_ID))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo("LIVE_ROOM_NOT_LIVE"));
    }

    @Test
    @DisplayName("校验通过并滑动续期：身份与签发一致")
    void validate_success_renewsTtl() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());
        var issued = service.issue(VIEWER_USER_ID, ROOM_ID);
        String serialized = objectMapper.writeValueAsString(new WebrtcSignalTicketService.TicketIdentity(
                VIEWER_USER_ID, ROOM_ID, "VIEWER", issued.peerSessionId()));
        when(valueOperations.get("live:signal_ticket:" + issued.ticket())).thenReturn(serialized);

        Optional<WebrtcSignalTicketService.TicketIdentity> identity =
                service.validate(issued.ticket(), ROOM_ID);

        assertThat(identity).isPresent();
        assertThat(identity.get().role()).isEqualTo("VIEWER");
        assertThat(identity.get().peerSessionId()).isEqualTo(issued.peerSessionId());
        verify(redisTemplate).expire(eq("live:signal_ticket:" + issued.ticket()), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("跨房间使用票据：拒绝")
    void validate_crossRoom_rejected() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());
        var issued = service.issue(VIEWER_USER_ID, ROOM_ID);
        String serialized = objectMapper.writeValueAsString(new WebrtcSignalTicketService.TicketIdentity(
                VIEWER_USER_ID, ROOM_ID, "VIEWER", issued.peerSessionId()));
        when(valueOperations.get("live:signal_ticket:" + issued.ticket())).thenReturn(serialized);

        assertThat(service.validate(issued.ticket(), 8L)).isEmpty();
    }

    @Test
    @DisplayName("Redis 故障 fail-closed：签发抛异常、校验返回 empty")
    void redisFailure_failClosed() {
        when(liveRoomMapper.selectById(ROOM_ID)).thenReturn(liveRoom());
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));

        assertThatThrownBy(() -> service.issue(VIEWER_USER_ID, ROOM_ID))
                .isInstanceOf(IllegalStateException.class);

        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        assertThat(service.validate("any-ticket", ROOM_ID)).isEmpty();
    }
}
