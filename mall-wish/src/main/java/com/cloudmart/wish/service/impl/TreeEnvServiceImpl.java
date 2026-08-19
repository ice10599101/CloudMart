package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.service.impl.MoodAggregator.MoodAggregate;
import com.cloudmart.wish.service.impl.MoodAggregator.MoodSample;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionInput;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionResult;
import com.cloudmart.wish.vo.TreeEnvVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 生命树情绪环境联动实现（文档 2.2 气象情绪联动 / Sprint 2.2）。
 *
 * <p><b>数据源决策（对文档 2.2 原文的偏差）</b>：文档原文为 mall-job 每
 * 5 分钟拉取 TREE_HOLE 心愿文本调 DashScope 情感分析；实际复用 Sprint 1.3
 * 树洞 AI 回复已产出的 {@code wish_ai_conversation.sentiment_score}
 * （仅 TREE_HOLE 场景 ASSISTANT 记录）——零额外 AI 成本、避免重复外发
 * 用户文本、隐私链路更短。详见 docs/tree-env-mood-design.md。</p>
 *
 * <p><b>Redis 策略</b>：</p>
 * <ul>
 *   <li>聚合分数缓存 {@code wish:tree:mood}，TTL 10 分钟（文档 2.2）；
 *       写失败仅告警（Fail-Open，下次扫描重写）</li>
 *   <li>扫描互斥锁 {@code wish:tree:mood:scan-lock}（SET NX EX），
 *       多实例并发扫描拿不到锁直接返回当前状态</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TreeEnvServiceImpl implements TreeEnvService {

    /** 聚合情绪分数 Redis 缓存 Key（文档 2.2 指定；public 供集成测试断言） */
    public static final String MOOD_CACHE_KEY = "wish:tree:mood";
    /** 扫描互斥锁 Key（TTL 略大于单次扫描耗时；public 供集成测试构造占用态） */
    public static final String SCAN_LOCK_KEY = "wish:tree:mood:scan-lock";

    private final WishWorldTreeStateMapper stateMapper;
    private final WishAiConversationMapper conversationMapper;
    private final WishInteractionMapper interactionMapper;
    private final StringRedisTemplate redisTemplate;
    private final WishTreeEnvProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public TreeEnvVO getCurrentEnv() {
        WishWorldTreeState state = stateMapper.selectById(WishWorldTreeState.SINGLETON_ID);
        if (state == null) {
            return new TreeEnvVO(TreeEnvironment.SUNNY, TreeEnvSource.INIT,
                    null, null, null, null, 0);
        }
        MoodCacheValue cached = readMoodCache();
        return toVO(state, cached != null ? cached.score() : null);
    }

    @Override
    public TreeEnvVO scan() {
        if (!tryAcquireScanLock()) {
            log.info("生命树情绪扫描锁被占用（其他实例扫描中），跳过本次扫描");
            return getCurrentEnv();
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            MoodAggregate aggregate = aggregateWindowMood(now);
            boolean blessBurst = detectBlessBurst(now);
            WishWorldTreeState state = loadOrInitState();

            TransitionInput input = new TransitionInput(
                    state.getEnvironment(), state.getEnvironmentSource(),
                    state.getTriggeredAt(), state.getExpiresAt(),
                    aggregate.score(), blessBurst, now);
            TransitionResult result = TreeEnvStateMachine.determine(input, props);

            applyTransition(state, result, aggregate.sampleCount(), now);
            refreshMoodCache(aggregate, now);
            log.info("生命树情绪扫描完成: environment={}, source={}, mood={}, samples={}, blessBurst={}",
                    result.environment(), result.source(), aggregate.score(),
                    aggregate.sampleCount(), blessBurst);
            return toVO(state, aggregate.score());
        } finally {
            releaseScanLock();
        }
    }

    /** 窗口内 TREE_HOLE ASSISTANT 记录的时间衰减加权平均（文档 2.2） */
    private MoodAggregate aggregateWindowMood(LocalDateTime now) {
        LocalDateTime windowStart = now.minusMinutes(props.getMoodWindowMinutes());
        List<WishAiConversation> records = conversationMapper.selectList(
                new LambdaQueryWrapper<WishAiConversation>()
                        .eq(WishAiConversation::getScene, AiScene.TREE_HOLE)
                        .eq(WishAiConversation::getRole, AiConversationRole.ASSISTANT)
                        .isNotNull(WishAiConversation::getSentimentScore)
                        .ge(WishAiConversation::getCreatedAt, windowStart));
        List<MoodSample> samples = records.stream()
                .map(r -> new MoodSample(r.getSentimentScore(), r.getCreatedAt()))
                .toList();
        return MoodAggregator.aggregate(samples, now, props.getMoodDecayLambda());
    }

    /**
     * BLESS 突增检测：当前窗口计数 ≥ 最小阈值 且 ≥ 前一窗口计数 × 倍率
     * （文档 2.2 未定义精确口径，参数经 Nacos 可调）。
     */
    private boolean detectBlessBurst(LocalDateTime now) {
        int windowMinutes = props.getBlessBurstWindowMinutes();
        long currentCount = countBlessBetween(now.minusMinutes(windowMinutes), now);
        long previousCount = countBlessBetween(now.minusMinutes(windowMinutes * 2L),
                now.minusMinutes(windowMinutes));
        boolean burst = currentCount >= props.getBlessBurstMinCount()
                && currentCount >= previousCount * props.getBlessBurstMultiplier();
        if (burst) {
            log.info("检测到 BLESS 祝福突增: current={}, previous={}, window={}min",
                    currentCount, previousCount, windowMinutes);
        }
        return burst;
    }

    private long countBlessBetween(LocalDateTime from, LocalDateTime to) {
        return interactionMapper.selectCount(new LambdaQueryWrapper<WishInteraction>()
                .eq(WishInteraction::getType, InteractionType.BLESS)
                .ge(WishInteraction::getCreatedAt, from)
                .lt(WishInteraction::getCreatedAt, to));
    }

    /** 加载单行状态；不存在时初始化 SUNNY（首次部署/表清空后自动恢复） */
    private WishWorldTreeState loadOrInitState() {
        WishWorldTreeState state = stateMapper.selectById(WishWorldTreeState.SINGLETON_ID);
        if (state != null) {
            return state;
        }
        WishWorldTreeState initial = new WishWorldTreeState();
        initial.setId(WishWorldTreeState.SINGLETON_ID);
        initial.setEnvironment(TreeEnvironment.SUNNY);
        initial.setEnvironmentSource(TreeEnvSource.INIT);
        initial.setSampleCount(0);
        try {
            stateMapper.insert(initial);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发初始化兜底：另一请求已插入单行，读取即可
        }
        WishWorldTreeState existing = stateMapper.selectById(WishWorldTreeState.SINGLETON_ID);
        return existing != null ? existing : initial;
    }

    /** 持久化状态机流转结果（lastScanAt/sampleCount 恒更新，环境变更按需更新） */
    private void applyTransition(WishWorldTreeState state, TransitionResult result,
                                 int sampleCount, LocalDateTime now) {
        // 显式 set（含 null）：updateById 会跳过 null 字段，导致 RAINBOW→RAIN
        // 时旧 expires_at 残留（RAIN 语义为无过期）
        stateMapper.update(null, new LambdaUpdateWrapper<WishWorldTreeState>()
                .eq(WishWorldTreeState::getId, state.getId())
                .set(WishWorldTreeState::getEnvironment, result.environment())
                .set(WishWorldTreeState::getEnvironmentSource, result.source())
                .set(WishWorldTreeState::getTriggeredAt, result.triggeredAt())
                .set(WishWorldTreeState::getExpiresAt, result.expiresAt())
                .set(WishWorldTreeState::getLastScanAt, now)
                .set(WishWorldTreeState::getSampleCount, sampleCount));

        state.setEnvironment(result.environment());
        state.setEnvironmentSource(result.source());
        state.setTriggeredAt(result.triggeredAt());
        state.setExpiresAt(result.expiresAt());
        state.setLastScanAt(now);
        state.setSampleCount(sampleCount);
    }

    /** 写聚合分数缓存（Fail-Open：失败仅告警，不影响状态流转） */
    private void refreshMoodCache(MoodAggregate aggregate, LocalDateTime now) {
        if (aggregate.score() == null) {
            return;
        }
        try {
            MoodCacheValue value = new MoodCacheValue(aggregate.score(), now.toString(),
                    aggregate.sampleCount());
            redisTemplate.opsForValue().set(MOOD_CACHE_KEY,
                    objectMapper.writeValueAsString(value),
                    Duration.ofMinutes(props.getMoodCacheTtlMinutes()));
        } catch (RedisConnectionFailureException | JsonProcessingException ex) {
            log.warn("生命树情绪缓存写入失败（Fail-Open，下次扫描重写）: {}", ex.getMessage());
        }
    }

    private MoodCacheValue readMoodCache() {
        try {
            String json = redisTemplate.opsForValue().get(MOOD_CACHE_KEY);
            return json != null ? objectMapper.readValue(json, MoodCacheValue.class) : null;
        } catch (RedisConnectionFailureException | JsonProcessingException ex) {
            log.warn("生命树情绪缓存读取失败（降级返回 null）: {}", ex.getMessage());
            return null;
        }
    }

    private boolean tryAcquireScanLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    SCAN_LOCK_KEY, "1", props.getScanLockTtlSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException ex) {
            // Redis 不可用时仍执行扫描（无锁保护可容忍：单行幂等写入）
            log.warn("生命树扫描锁获取失败，Redis 不可用，无锁继续扫描（Fail-Open）: {}", ex.getMessage());
            return true;
        }
    }

    private void releaseScanLock() {
        try {
            redisTemplate.delete(SCAN_LOCK_KEY);
        } catch (RedisConnectionFailureException ex) {
            log.warn("生命树扫描锁释放失败（TTL 兜底自动过期）: {}", ex.getMessage());
        }
    }

    private TreeEnvVO toVO(WishWorldTreeState state, Double moodScore) {
        return new TreeEnvVO(state.getEnvironment(), state.getEnvironmentSource(),
                state.getTriggeredAt(), state.getExpiresAt(), state.getLastScanAt(),
                moodScore, state.getSampleCount());
    }

    /** Redis 聚合分数缓存结构（不落库，文档 2.2 隐私约束） */
    record MoodCacheValue(Double score, String computedAt, int sampleCount) {}
}
