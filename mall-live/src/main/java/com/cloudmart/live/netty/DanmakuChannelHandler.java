package com.cloudmart.live.netty;

import com.cloudmart.live.dto.DanmakuMessage;
import com.cloudmart.live.entity.LiveDanmaku;
import com.cloudmart.live.repository.LiveDanmakuMapper;
import tools.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 弹幕频道管理器。
 * 使用 Netty ChannelGroup 管理直播间内的所有连接，
 * 支持按房间广播弹幕。通过 Redis Pub/Sub 实现跨节点广播。
 */
@Component
public class DanmakuChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(DanmakuChannelHandler.class);
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int RATE_LIMIT_PER_USER_PER_MINUTE = 30;
    private static final String RATE_LIMIT_PREFIX = "live:rate:";

    private final Map<Long, ChannelGroup> roomChannels = new ConcurrentHashMap<>();
    private final Map<String, Long> channelRoomMap = new ConcurrentHashMap<>();
    private final Map<String, Long> channelUserMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final LiveDanmakuMapper danmakuMapper;
    private final StringRedisTemplate redisTemplate;

    public DanmakuChannelHandler(ObjectMapper objectMapper,
                                  LiveDanmakuMapper danmakuMapper,
                                  StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.danmakuMapper = danmakuMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 用户加入直播间
     */
    public void joinRoom(Channel channel, Long roomId, Long userId) {
        ChannelGroup group = roomChannels.computeIfAbsent(roomId,
                k -> new DefaultChannelGroup(roomId.toString(), GlobalEventExecutor.INSTANCE));
        group.add(channel);
        channelRoomMap.put(channel.id().asLongText(), roomId);
        channelUserMap.put(channel.id().asLongText(), userId);

        log.debug("User {} joined room {}", userId, roomId);
    }

    /**
     * 用户离开直播间
     */
    public void leaveRoom(Channel channel) {
        String channelId = channel.id().asLongText();
        Long roomId = channelRoomMap.remove(channelId);
        channelUserMap.remove(channelId);

        if (roomId != null) {
            ChannelGroup group = roomChannels.get(roomId);
            if (group != null) {
                group.remove(channel);
                if (group.isEmpty()) {
                    roomChannels.remove(roomId);
                }
            }
        }
    }

    /**
     * 处理弹幕消息：限流 -> 校验 -> 持久化 -> 广播
     */
    public void handleDanmaku(Channel sender, String messageJson) {
        String channelId = sender.id().asLongText();
        Long roomId = channelRoomMap.get(channelId);
        Long userId = channelUserMap.get(channelId);

        if (roomId == null || userId == null) {
            return;
        }

        // 限流：每用户每分钟最多发送 N 条弹幕
        if (!checkRateLimit(userId, roomId)) {
            return;
        }

        DanmakuMessage danmaku;
        try {
            danmaku = objectMapper.readValue(messageJson, DanmakuMessage.class);
        } catch (Exception e) {
            return;
        }

        if (danmaku.content() == null || danmaku.content().isBlank()
                || danmaku.content().length() > MAX_CONTENT_LENGTH) {
            return;
        }

        DanmakuMessage enriched = new DanmakuMessage(
                roomId, userId, danmaku.nickname(),
                sanitizeContent(danmaku.content()),
                System.currentTimeMillis()
        );

        // 异步持久化
        persistDanmaku(enriched);

        // 房间内广播
        broadcastToRoom(roomId, enriched);
    }

    /**
     * 向指定房间广播弹幕
     */
    public void broadcastToRoom(Long roomId, DanmakuMessage message) {
        ChannelGroup group = roomChannels.get(roomId);
        if (group == null || group.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            group.writeAndFlush(json);
        } catch (Exception e) {
            log.error("Failed to broadcast danmaku to room {}: {}", roomId, e.getMessage());
        }
    }

    private boolean checkRateLimit(Long userId, Long roomId) {
        String key = RATE_LIMIT_PREFIX + roomId + ":" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        return count != null && count <= RATE_LIMIT_PER_USER_PER_MINUTE;
    }

    private void persistDanmaku(DanmakuMessage message) {
        try {
            LiveDanmaku entity = new LiveDanmaku();
            entity.setRoomId(message.roomId());
            entity.setUserId(message.userId());
            entity.setNickname(message.nickname());
            entity.setContent(message.content());
            danmakuMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to persist danmaku: {}", e.getMessage());
        }
    }

    private String sanitizeContent(String content) {
        return content.replaceAll("<[^>]*>", "")
                .replaceAll("[\\r\\n]", " ")
                .trim();
    }
}
