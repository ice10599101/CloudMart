package com.cloudmart.live.service;

import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.repository.LiveRoomMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * WebRTC 信令票据（T08）：房间级授权与连接隔离的信任载体。
 *
 * <p>角色由服务端权威派生：请求者 userId 等于房间 {@code anchorUserId} 才是 HOST，
 * 其余一律 VIEWER——客户端自报 role 不再被信任（旧实现凭请求体 role 写任意房间
 * 的 HOST 键，可冒充主播推送恶意 SDP）。票据绑定 roomId+userId+role+peerSessionId，
 * 观众协商数据按 peerSessionId 隔离，重连换新会话互不串扰。</p>
 *
 * <p>票据为短时会话凭证（60s 滑动续期）：每次合法使用刷新过期时间，会话活跃期间
 * 无需反复签发；断网重连/过期后客户端重新签发，得到全新 peerSessionId。
 * Redis 故障 fail-closed：签发/校验抛异常，信令全拒绝——宁可推不了流，不允许
 * 未授权房间写入。</p>
 */
@Slf4j
@Service
public class WebrtcSignalTicketService {

    private static final String TICKET_KEY_PREFIX = "live:signal_ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LiveRoomMapper liveRoomMapper;

    public WebrtcSignalTicketService(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     LiveRoomMapper liveRoomMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.liveRoomMapper = liveRoomMapper;
    }

    /** 一次签发的结果：票据、服务端派生角色、对端会话隔离 ID 与过期时间 */
    public record IssuedTicket(String ticket, String role, String peerSessionId, Instant expiresAt) {
    }

    /** 票据绑定的信令身份（服务端签发时确定，信令操作以此为唯一权威） */
    public record TicketIdentity(Long userId, Long roomId, String role, String peerSessionId) {
    }

    /**
     * 为当前用户签发指定直播间的信令票据。
     *
     * @throws com.cloudmart.common.exception.BusinessException 房间不存在/未在直播
     */
    public IssuedTicket issue(Long userId, Long roomId) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "LIVE_ROOM_NOT_FOUND", "直播间不存在");
        }
        if (!"LIVE".equals(room.getStatus())) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "LIVE_ROOM_NOT_LIVE", "直播间未在直播，不能建立信令会话");
        }
        String role = userId != null && userId.equals(room.getAnchorUserId()) ? "HOST" : "VIEWER";
        String ticket = UUID.randomUUID().toString();
        String peerSessionId = UUID.randomUUID().toString();
        try {
            redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket,
                    objectMapper.writeValueAsString(new TicketIdentity(userId, roomId, role, peerSessionId)),
                    TICKET_TTL);
        } catch (Exception e) {
            log.error("[T08] 信令票据签发失败（Redis 不可用？）: {}", e.getMessage());
            throw new IllegalStateException("信令票据服务暂不可用", e);
        }
        log.info("[T08] 信令票据签发 roomId={} userId={} role={} peerSession={}",
                roomId, userId, role, peerSessionId);
        return new IssuedTicket(ticket, role, peerSessionId, Instant.now().plusSeconds(TICKET_TTL.toSeconds()));
    }

    /**
     * 校验票据并刷新过期时间（滑动会话）。角色/房间/身份一律以票据为准。
     *
     * @return 票据身份；无效/过期/跨房间返回 empty
     */
    public Optional<TicketIdentity> validate(String ticket, Long roomId) {
        if (ticket == null || ticket.isBlank() || roomId == null) {
            return Optional.empty();
        }
        String key = TICKET_KEY_PREFIX + ticket;
        try {
            String serialized = redisTemplate.opsForValue().get(key);
            if (serialized == null) {
                return Optional.empty();
            }
            TicketIdentity identity = objectMapper.readValue(serialized, TicketIdentity.class);
            if (!roomId.equals(identity.roomId())) {
                log.warn("[T08] 信令票据房间不匹配 ticketRoom={} requestRoom={}",
                        identity.roomId(), roomId);
                return Optional.empty();
            }
            // 滑动续期：会话活跃期间票据持续可用；过期后客户端重新签发新会话
            redisTemplate.expire(key, TICKET_TTL);
            return Optional.of(identity);
        } catch (Exception e) {
            log.error("[T08] 信令票据校验失败（fail-closed 拒绝信令）: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
