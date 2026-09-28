package com.cloudmart.live.service;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 直播 WS 一次性票据（LIVE-01）：握手认证的信任载体。
 *
 * <p>签发：已登录用户为指定房间换取 30 秒一次性 ticket；消费：握手时校验并
 * 原子删除（DEL 返回 1 才算消费成功）——过期/重放/跨房间票据一律拒绝。
 * 弹幕身份以票据绑定的 userId 为准，客户端载荷中的身份声明不再被信任。</p>
 *
 * <p>Redis 故障时 fail-closed：签发/消费抛异常，握手拒绝——宁可连不上，
 * 不允许未认证写消息。</p>
 */
@Slf4j
@Service
public class LiveWsTicketService {

    private static final String TICKET_KEY_PREFIX = "live:ws_ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LiveWsTicketService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 一次签发的结果：票据、过期时间与 WS 路径（含 ticket，客户端直连用） */
    public record IssuedTicket(String ticket, Instant expiresAt, String wsPath) {
    }

    /** 票据绑定的身份（服务端签发时确定，握手后写入会话） */
    public record TicketIdentity(Long userId, String nickname, Long roomId) {
    }

    public IssuedTicket issue(Long userId, String nickname, Long roomId) {
        String ticket = UUID.randomUUID().toString();
        try {
            redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket,
                    objectMapper.writeValueAsString(new TicketIdentity(userId, nickname, roomId)),
                    TICKET_TTL);
        } catch (Exception e) {
            log.error("[LIVE01] WS 票据签发失败（Redis 不可用？）: {}", e.getMessage());
            throw new IllegalStateException("WS 票据服务暂不可用", e);
        }
        String wsPath = "/ws/live/danmaku?roomId=" + roomId + "&ticket=" + ticket;
        return new IssuedTicket(ticket, Instant.now().plusSeconds(TICKET_TTL.toSeconds()), wsPath);
    }

    /**
     * 消费票据：存在、 roomId 匹配且删除成功（一次性）才返回身份。
     *
     * @return 票据身份；无效/过期/重放/跨房间返回 empty
     */
    public Optional<TicketIdentity> consume(String ticket, Long roomId) {
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
                // 跨房间使用：票据与房间绑定，拒绝且不消费（供审计观察重放目标）
                log.warn("[LIVE01] WS 票据房间不匹配 ticketRoom={} requestRoom={}",
                        identity.roomId(), roomId);
                return Optional.empty();
            }
            // 原子消费：删除成功者才获得身份——并发重放只有一个赢家
            Boolean deleted = redisTemplate.delete(key);
            if (!Boolean.TRUE.equals(deleted)) {
                log.warn("[LIVE01] WS 票据重放被拒绝 ticket={}", ticket);
                return Optional.empty();
            }
            return Optional.of(identity);
        } catch (Exception e) {
            log.error("[LIVE01] WS 票据校验失败（fail-closed 拒绝握手）: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
