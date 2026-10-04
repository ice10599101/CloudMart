package com.cloudmart.live.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import com.cloudmart.live.service.WebrtcService;
import com.cloudmart.live.service.WebrtcSignalTicketService;
import com.cloudmart.live.service.WebrtcSignalTicketService.TicketIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * WebRTC 信令服务（T08 重构）：房间级授权 + 观众会话隔离。
 *
 * <p>旧实现按客户端自报 role 写 (roomId, role) 全房间共享键：任何登录用户都能
 * 冒充任意房间的主播（伪造 OFFER 投毒）、读取其他观众的私有协商（SDP/ICE 泄露）、
 * 清掉他人直播间信令。现所有操作以 {@link WebrtcSignalTicketService} 签发的票据
 * 为唯一身份源：</p>
 *
 * <ul>
 *   <li>HOST（服务端按 anchorUserId 派生）：OFFER/ICE 写共享键，重连换新会话时
 *       旧 OFFER 整键替换——旧会话不能干扰新会话；可聚合读取全部观众会话；</li>
 *   <li>VIEWER：ANSWER/ICE 仅写入自身 {@code peerSessionId} 会话键，读取
 *       targetRole=VIEWER 时服务端强制只返回自身会话——观众之间互不可见；</li>
 *   <li>清除信令仅限 HOST 票据；观众会话数据随短 TTL 自然过期；</li>
 *   <li>SDP/ICE 正文 ≤64KB，单票据每分钟 ≤120 次发布（超限明确拒绝）；</li>
 *   <li>票据无效/过期/跨房间：信令全拒绝（fail-closed），客户端重新签发新会话。</li>
 * </ul>
 */
@Service
public class WebrtcServiceImpl implements WebrtcService {

    private static final Logger log = LoggerFactory.getLogger(WebrtcServiceImpl.class);
    private static final String SIGNAL_KEY_PREFIX = "live:webrtc:signal:";
    private static final String ICE_KEY_PREFIX = "live:webrtc:ice:";
    private static final String RATE_LIMIT_KEY_PREFIX = "live:webrtc:rl:";
    /** HOST 共享键存活（对齐旧行为）；观众会话键用短 TTL（重连旧会话自动收敛） */
    private static final long SHARED_TTL_SECONDS = 3600;
    private static final long SESSION_TTL_SECONDS = 600;
    private static final int MAX_PAYLOAD_CHARS = 65_536;
    private static final int MAX_PUBLISH_PER_MINUTE = 120;
    private static final String ROLE_HOST = "HOST";
    private static final String ROLE_VIEWER = "VIEWER";

    private final StringRedisTemplate redisTemplate;
    private final WebrtcSignalTicketService ticketService;

    public WebrtcServiceImpl(StringRedisTemplate redisTemplate,
                             WebrtcSignalTicketService ticketService) {
        this.redisTemplate = redisTemplate;
        this.ticketService = ticketService;
    }

    @Override
    public WebrtcSignalTicketService.IssuedTicket issueSignalTicket(Long userId, Long roomId) {
        return ticketService.issue(userId, roomId);
    }

    @Override
    public void publishSignal(WebrtcSignalRequest request) {
        TicketIdentity identity = requireIdentity(request.ticket(), request.roomId());
        checkPayloadLimit(request.payload());
        checkPublishRate(request.ticket());

        if (ROLE_HOST.equals(identity.role())) {
            if (!"OFFER".equals(request.type())) {
                throw new BusinessException("SIGNAL_TYPE_NOT_ALLOWED", "主播仅能发布 OFFER 信令");
            }
            // 新会话 OFFER 整键替换：观众不会连到旧会话的陈旧 Offer（重连确定性）
            String key = sharedSignalKey(identity.roomId());
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPush(key, request.type() + "|" + request.payload());
            redisTemplate.expire(key, SHARED_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("[T08] HOST OFFER published: roomId={}, peerSession={}",
                    identity.roomId(), identity.peerSessionId());
            return;
        }
        if (!"ANSWER".equals(request.type())) {
            throw new BusinessException("SIGNAL_TYPE_NOT_ALLOWED", "观众仅能提交 ANSWER 信令");
        }
        String sessionKey = viewerSignalKey(identity.roomId(), identity.peerSessionId());
        redisTemplate.opsForList().rightPush(sessionKey, request.type() + "|" + request.payload());
        redisTemplate.expire(sessionKey, SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<WebrtcSignalResponse> getSignals(Long roomId, String targetRole, String ticket) {
        TicketIdentity identity = requireIdentity(ticket, roomId);
        if (ROLE_HOST.equals(targetRole)) {
            return readSignals(sharedSignalKey(roomId), targetRole);
        }
        if (ROLE_VIEWER.equals(targetRole)) {
            if (ROLE_HOST.equals(identity.role())) {
                // 主播是所有观众会话的对端：聚合全部观众 ANSWER（键按会话隔离）
                List<WebrtcSignalResponse> aggregated = new ArrayList<>();
                for (String sessionKey : scanKeys(SIGNAL_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":*")) {
                    aggregated.addAll(readSignals(sessionKey, ROLE_VIEWER));
                }
                return aggregated;
            }
            // 观众只能读自身会话：目标键由票据 peerSessionId 决定，不接受任意会话枚举
            return readSignals(viewerSignalKey(roomId, identity.peerSessionId()), ROLE_VIEWER);
        }
        throw new BusinessException("SIGNAL_ROLE_INVALID", "信令角色非法: " + targetRole);
    }

    @Override
    public void publishIceCandidate(WebrtcSignalRequest request) {
        TicketIdentity identity = requireIdentity(request.ticket(), request.roomId());
        checkPayloadLimit(request.payload());
        checkPublishRate(request.ticket());
        if (!"ICE_CANDIDATE".equals(request.type())) {
            throw new BusinessException("SIGNAL_TYPE_NOT_ALLOWED", "ICE 通道仅接受 ICE_CANDIDATE 类型");
        }
        String key = ROLE_HOST.equals(identity.role())
                ? sharedIceKey(identity.roomId())
                : viewerIceKey(identity.roomId(), identity.peerSessionId());
        redisTemplate.opsForList().rightPush(key, request.payload());
        redisTemplate.expire(key,
                ROLE_HOST.equals(identity.role()) ? SHARED_TTL_SECONDS : SESSION_TTL_SECONDS,
                TimeUnit.SECONDS);
    }

    @Override
    public List<String> getIceCandidates(Long roomId, String targetRole, String ticket) {
        TicketIdentity identity = requireIdentity(ticket, roomId);
        if (ROLE_HOST.equals(targetRole)) {
            List<String> candidates = redisTemplate.opsForList().range(sharedIceKey(roomId), 0, -1);
            return candidates != null ? candidates : List.of();
        }
        if (ROLE_VIEWER.equals(targetRole)) {
            if (ROLE_HOST.equals(identity.role())) {
                List<String> aggregated = new ArrayList<>();
                for (String sessionKey : scanKeys(ICE_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":*")) {
                    List<String> candidates = redisTemplate.opsForList().range(sessionKey, 0, -1);
                    if (candidates != null) {
                        aggregated.addAll(candidates);
                    }
                }
                return aggregated;
            }
            List<String> own = redisTemplate.opsForList()
                    .range(viewerIceKey(roomId, identity.peerSessionId()), 0, -1);
            return own != null ? own : List.of();
        }
        throw new BusinessException("SIGNAL_ROLE_INVALID", "信令角色非法: " + targetRole);
    }

    @Override
    public void clearSignals(Long roomId, String ticket) {
        TicketIdentity identity = requireIdentity(ticket, roomId);
        if (!ROLE_HOST.equals(identity.role())) {
            log.warn("[T08] 非房主尝试清除房间信令 roomId={} userId={} role={}",
                    roomId, identity.userId(), identity.role());
            throw new BusinessException("SIGNAL_CLEAR_FORBIDDEN", "仅主播可清除直播间信令");
        }
        redisTemplate.delete(sharedSignalKey(roomId));
        redisTemplate.delete(sharedIceKey(roomId));
        List<String> sessionKeys = new ArrayList<>();
        sessionKeys.addAll(scanKeys(SIGNAL_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":*"));
        sessionKeys.addAll(scanKeys(ICE_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":*"));
        if (!sessionKeys.isEmpty()) {
            redisTemplate.delete(sessionKeys);
        }
        log.info("[T08] 房间信令已清除 roomId={} by host userId={}", roomId, identity.userId());
    }

    /** 票据校验统一入口：无效即拒绝（不泄露存在性差异），房间必须与票据一致 */
    private TicketIdentity requireIdentity(String ticket, Long roomId) {
        Optional<TicketIdentity> identity = ticketService.validate(ticket, roomId);
        return identity.orElseThrow(() -> new BusinessException(
                "SIGNAL_TICKET_INVALID", "信令票据无效或已过期，请重新建立会话"));
    }

    private void checkPayloadLimit(String payload) {
        if (payload != null && payload.length() > MAX_PAYLOAD_CHARS) {
            throw new BusinessException("SIGNAL_PAYLOAD_TOO_LARGE",
                    "信令正文超过 " + MAX_PAYLOAD_CHARS + " 字符上限");
        }
    }

    /** 单票据发布频率上限（INCR+60s TTL 滑动窗口；Redis 故障不阻断主流程） */
    private void checkPublishRate(String ticket) {
        try {
            String key = RATE_LIMIT_KEY_PREFIX + ticket;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            if (count != null && count > MAX_PUBLISH_PER_MINUTE) {
                throw new BusinessException("SIGNAL_RATE_LIMITED",
                        "信令发布频率超限（每分钟 " + MAX_PUBLISH_PER_MINUTE + " 次），请稍后重试");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[T08] 信令频率统计不可用（放行主流程）: {}", ex.getMessage());
        }
    }

    private List<WebrtcSignalResponse> readSignals(String key, String role) {
        List<String> rawSignals = redisTemplate.opsForList().range(key, 0, -1);
        List<WebrtcSignalResponse> responses = new ArrayList<>();
        if (rawSignals != null) {
            for (String raw : rawSignals) {
                String[] parts = raw.split("\\|", 2);
                if (parts.length == 2) {
                    responses.add(new WebrtcSignalResponse(parts[0], parts[1], role));
                }
            }
        }
        return responses;
    }

    /** SCAN 替代 KEYS：观众会话键枚举不阻塞 Redis（键规模=活跃观众数） */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception ex) {
            log.warn("[T08] 会话键扫描失败 pattern={}: {}", pattern, ex.getMessage());
        }
        return keys;
    }

    private static String sharedSignalKey(Long roomId) {
        return SIGNAL_KEY_PREFIX + roomId + ":" + ROLE_HOST;
    }

    private static String viewerSignalKey(Long roomId, String peerSessionId) {
        return SIGNAL_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":" + peerSessionId;
    }

    private static String sharedIceKey(Long roomId) {
        return ICE_KEY_PREFIX + roomId + ":" + ROLE_HOST;
    }

    private static String viewerIceKey(Long roomId, String peerSessionId) {
        return ICE_KEY_PREFIX + roomId + ":" + ROLE_VIEWER + ":" + peerSessionId;
    }
}
