package com.cloudmart.community.service.impl;

import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.mq.CommunityEventProducer.LikeTimesMessage;
import com.cloudmart.community.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis 的点赞服务实现。
 *
 * <p>数据结构：
 * <ul>
 *   <li>Redis Set {@code likes:set:{type}:{targetId}} — 记录点赞该目标的用户ID集合（SADD/SREM/SISMEMBER）</li>
 *   <li>Redis ZSet {@code likes:user:{type}:{userId}} — 记录用户点赞过的目标ID，score 为点赞时间戳（ZADD/ZREM/ZREVRANGE）</li>
 *   <li>Redis ZSet {@code likes:times:{type}} — 待同步的点赞数变更队列，member=targetId，score=净增量（ZINCRBY/ZPOPMIN）</li>
 * </ul>
 *
 * <p>TTL 策略：Set 和用户 ZSet 设置 30 天 TTL + 随机抖动，每次点赞时刷新。
 * 待同步 ZSet 不设 TTL，由定时任务持续消费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final StringRedisTemplate redisTemplate;
    private final CommunityEventProducer communityEventProducer;

    private static final String BIZ_SET_PREFIX = "likes:set:";
    private static final String USER_ZSET_PREFIX = "likes:user:";
    private static final String TIMES_ZSET_PREFIX = "likes:times:";

    /** Set / 用户 ZSet 的基础 TTL */
    private static final Duration BASE_TTL = Duration.ofDays(30);
    /** 随机抖动范围（秒），防止缓存雪崩 */
    private static final int JITTER_SECONDS = 3600;
    /** 定时任务单次从 ZSet 弹出的最大条数 */
    private static final int SYNC_BATCH_SIZE = 100;

    // ======================== 写操作 ========================

    @Override
    public boolean like(Long userId, String targetType, Long targetId) {
        String userIdStr = String.valueOf(userId);
        String bizSetKey = bizSetKey(targetType, targetId);
        String userZsetKey = userZsetKey(targetType, userId);
        String timesZsetKey = timesZsetKey(targetType);

        // SADD 原子去重：返回 1 表示新增成功，0 表示已存在
        Long added = redisTemplate.opsForSet().add(bizSetKey, userIdStr);
        if (added == null || added == 0L) {
            return false;
        }

        // 刷新 biz Set TTL
        refreshTtl(bizSetKey);
        // 记录用户点赞列表（ZSet，score 为当前时间戳，支持按时间倒序分页）
        redisTemplate.opsForZSet().add(userZsetKey, String.valueOf(targetId),
                System.currentTimeMillis() / 1000.0);
        refreshTtl(userZsetKey);
        // 累加待同步的点赞数增量
        redisTemplate.opsForZSet().incrementScore(timesZsetKey, String.valueOf(targetId), 1);

        return true;
    }

    @Override
    public boolean unlike(Long userId, String targetType, Long targetId) {
        String userIdStr = String.valueOf(userId);
        String bizSetKey = bizSetKey(targetType, targetId);
        String userZsetKey = userZsetKey(targetType, userId);
        String timesZsetKey = timesZsetKey(targetType);

        // SREM 原子删除：返回 1 表示删除成功，0 表示原本不存在
        Long removed = redisTemplate.opsForSet().remove(bizSetKey, userIdStr);
        if (removed == null || removed == 0L) {
            return false;
        }

        // 从用户点赞列表移除
        redisTemplate.opsForZSet().remove(userZsetKey, String.valueOf(targetId));
        // 累加待同步的点赞数负增量
        redisTemplate.opsForZSet().incrementScore(timesZsetKey, String.valueOf(targetId), -1);

        return true;
    }

    // ======================== 读操作 ========================

    @Override
    public boolean isLiked(Long userId, String targetType, Long targetId) {
        Boolean isMember = redisTemplate.opsForSet().isMember(
                bizSetKey(targetType, targetId), String.valueOf(userId));
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public Map<Long, Boolean> batchIsLiked(Long userId, String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String userIdStr = String.valueOf(userId);

        // 使用 Pipeline 批量执行 SISMEMBER，避免 N 次网络往返
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @SuppressWarnings("unchecked")
            @Override
            public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                for (Long targetId : targetIds) {
                    operations.opsForSet().isMember(bizSetKey(targetType, targetId), userIdStr);
                }
                return null;
            }
        });

        Map<Long, Boolean> resultMap = new LinkedHashMap<>(targetIds.size());
        for (int i = 0; i < targetIds.size(); i++) {
            Object result = results.get(i);
            resultMap.put(targetIds.get(i), Boolean.TRUE.equals(result));
        }
        return resultMap;
    }

    @Override
    public List<Long> getLikedTargetIds(Long userId, String targetType, int page, int size) {
        String userZsetKey = userZsetKey(targetType, userId);
        long offset = (long) (page - 1) * size;

        // ZREVRANGE 按点赞时间倒序分页
        Set<String> targetIdStrs = redisTemplate.opsForZSet().reverseRange(
                userZsetKey, offset, offset + size - 1);

        if (targetIdStrs == null || targetIdStrs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> targetIds = new ArrayList<>(targetIdStrs.size());
        for (String idStr : targetIdStrs) {
            try {
                targetIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                log.warn("Invalid targetId in user like ZSet: key={}, value={}", userZsetKey, idStr);
            }
        }
        return targetIds;
    }

    @Override
    public long countLiked(Long userId, String targetType) {
        Long count = redisTemplate.opsForZSet().zCard(userZsetKey(targetType, userId));
        return count != null ? count : 0L;
    }

    // ======================== 定时同步 ========================

    @Override
    public void syncLikedTimesToMQ() {
        String timesZsetKey = timesZsetKey("POST");

        // ZPOPMIN 弹出 score 最小的 SYNC_BATCH_SIZE 条记录
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .popMin(timesZsetKey, SYNC_BATCH_SIZE);

        if (tuples == null || tuples.isEmpty()) {
            return;
        }

        List<LikeTimesMessage> messages = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String targetIdStr = tuple.getValue();
            Double score = tuple.getScore();
            if (targetIdStr == null || score == null) {
                continue;
            }
            int delta = (int) Math.round(score);
            // delta 为 0 表示点赞和取消互相抵消，无需更新数据库
            if (delta == 0) {
                continue;
            }
            try {
                Long targetId = Long.parseLong(targetIdStr);
                messages.add(new LikeTimesMessage("POST", targetId, delta));
            } catch (NumberFormatException e) {
                log.warn("Invalid targetId in times ZSet: value={}", targetIdStr);
            }
        }

        if (!messages.isEmpty()) {
            communityEventProducer.publishLikeTimesBatch(messages);
            log.info("Synced like-times to MQ: count={}", messages.size());
        }
    }

    // ======================== 私有方法 ========================

    private String bizSetKey(String targetType, Long targetId) {
        return BIZ_SET_PREFIX + targetType + ":" + targetId;
    }

    private String userZsetKey(String targetType, Long userId) {
        return USER_ZSET_PREFIX + targetType + ":" + userId;
    }

    private String timesZsetKey(String targetType) {
        return TIMES_ZSET_PREFIX + targetType;
    }

    /**
     * 刷新 Key 的 TTL，加随机抖动防止雪崩。
     */
    private void refreshTtl(String key) {
        long ttlSeconds = BASE_TTL.getSeconds() + ThreadLocalRandom.current().nextInt(JITTER_SECONDS);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }
}
