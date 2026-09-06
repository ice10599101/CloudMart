package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.entity.WishEnvConfig;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishSpecialEvent;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.SpecialEventStatus;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.enums.TreeTimePhase;
import com.cloudmart.wish.enums.TreeWeather;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishEnvConfigMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishSpecialEventMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.service.impl.MoodAggregator.MoodAggregate;
import com.cloudmart.wish.service.impl.MoodAggregator.MoodSample;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionInput;
import com.cloudmart.wish.service.impl.TreeEnvStateMachine.TransitionResult;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.cloudmart.wish.vo.TreeEnvVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 生命树环境服务实现（文档 2.2 气象情绪联动 / Sprint 2.2 动态环境扩展）。
 *
 * <p><b>数据源决策（对文档 2.2 原文的偏差）</b>：文档原文为 mall-job 每
 * 5 分钟拉取 TREE_HOLE 心愿文本调大模型情感分析；实际复用 Sprint 1.3
 * 树洞 AI 回复已产出的 {@code wish_ai_conversation.sentiment_score}
 * （仅 TREE_HOLE 场景 ASSISTANT 记录）——零额外 AI 成本、避免重复外发
 * 用户文本、隐私链路更短。详见 docs/tree-env-mood-design.md。</p>
 *
 * <p><b>Sprint 2.2 多维环境模型</b>：</p>
 * <ul>
 *   <li>情绪环境 environment：Sprint 1.5 状态机（RAIN/RAINBOW）不变</li>
 *   <li>季节 season：mall-job 每日 00:00 扫描写入 state.season（UTC 日期
 *       判定，与 V10 回填口径一致）；未扫描时实时计算兜底</li>
 *   <li>天气 weather：{@link QWeatherClient}（和风天气 v7，Redis 5 分钟
 *       缓存，降级晴天）</li>
 *   <li>时段 timePhase：按客户端 UTC 偏移计算（文档验收：跨时区用户按
 *       本地时区而非服务器时区）</li>
 *   <li>特殊事件 specialEvent：管理员触发全站同步；惰性过期判定
 *       （expires_at 已过视同 ENDED，无需收尾定时任务）</li>
 *   <li>聚合展示 displayEnv：优先级 特殊事件 &gt; 情绪 RAINBOW/RAIN &gt;
 *       真实天气（SUNNY 情绪不覆盖真实天气）</li>
 * </ul>
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

    /** 合法时区偏移上限（分钟，UTC+14；超限视为非法参数按 UTC 处理） */
    private static final int MAX_TZ_OFFSET_MINUTES = 14 * 60;

    private final WishWorldTreeStateMapper stateMapper;
    private final WishAiConversationMapper conversationMapper;
    private final WishInteractionMapper interactionMapper;
    private final WishSpecialEventMapper specialEventMapper;
    private final WishEnvConfigMapper envConfigMapper;
    private final QWeatherClient weatherClient;
    private final StringRedisTemplate redisTemplate;
    private final WishTreeEnvProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public TreeEnvVO getCurrentEnv(Integer tzOffsetMinutes) {
        WishWorldTreeState state = stateMapper.selectById(WishWorldTreeState.SINGLETON_ID);
        MoodCacheValue cached = readMoodCache();
        return buildSnapshot(state, cached != null ? cached.score() : null, tzOffsetMinutes);
    }

    @Override
    public TreeEnvVO scan() {
        if (!tryAcquireScanLock()) {
            log.info("生命树情绪扫描锁被占用（其他实例扫描中），跳过本次扫描");
            return getCurrentEnv(0);
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
            return buildSnapshot(state, aggregate.score(), 0);
        } finally {
            releaseScanLock();
        }
    }

    @Override
    public TreeSeason scanSeason() {
        WishWorldTreeState state = loadOrInitState();
        TreeSeason season = currentSeason();
        if (season != state.getSeason()) {
            // 幂等：季节未变化不产生写；单行表主键更新无并发风险
            stateMapper.update(null, new LambdaUpdateWrapper<WishWorldTreeState>()
                    .eq(WishWorldTreeState::getId, state.getId())
                    .set(WishWorldTreeState::getSeason, season));
            log.info("生命树季节落库: {} -> {}", state.getSeason(), season);
        }
        return season;
    }

    @Override
    public SpecialEventVO getActiveSpecialEvent() {
        WishSpecialEvent event = specialEventMapper.selectOne(
                new LambdaQueryWrapper<WishSpecialEvent>()
                        .eq(WishSpecialEvent::getStatus, SpecialEventStatus.ACTIVE)
                        .orderByDesc(WishSpecialEvent::getTriggeredAt)
                        .last("LIMIT 1"));
        if (event == null) {
            return null;
        }
        if (event.getExpiresAt() != null && !event.getExpiresAt().isAfter(LocalDateTime.now())) {
            // 惰性过期：过期后首次读取时单次幂等写 ENDED，之后查询不再命中
            specialEventMapper.update(null, new LambdaUpdateWrapper<WishSpecialEvent>()
                    .eq(WishSpecialEvent::getId, event.getId())
                    .eq(WishSpecialEvent::getStatus, SpecialEventStatus.ACTIVE)
                    .set(WishSpecialEvent::getStatus, SpecialEventStatus.ENDED));
            log.info("特殊事件已过期自动结束: id={}, code={}", event.getId(), event.getEventCode());
            return null;
        }
        return toEventVO(event);
    }

    @Override
    public List<EnvConfigVO> listActiveEnvConfigs() {
        return envConfigMapper.selectList(new LambdaQueryWrapper<WishEnvConfig>()
                        .eq(WishEnvConfig::getIsActive, true)
                        .orderByDesc(WishEnvConfig::getPriority))
                .stream().map(this::toConfigVO).toList();
    }

    // ---------------- 多维环境聚合 ----------------

    /**
     * 组装全量环境快照（情绪/季节/天气/时段/特殊事件 + 聚合展示环境）。
     * state 为 null 时（表未初始化）情绪部分取默认 SUNNY/INIT。
     */
    private TreeEnvVO buildSnapshot(WishWorldTreeState state, Double moodScore,
                                    Integer tzOffsetMinutes) {
        TreeEnvironment environment = state != null ? state.getEnvironment() : TreeEnvironment.SUNNY;
        TreeEnvSource source = state != null ? state.getEnvironmentSource() : TreeEnvSource.INIT;
        TreeWeather weather = weatherClient.getCurrentWeather();
        SpecialEventVO specialEvent = getActiveSpecialEvent();
        return TreeEnvVO.builder()
                .environment(environment)
                .source(source)
                .triggeredAt(state != null ? state.getTriggeredAt() : null)
                .expiresAt(state != null ? state.getExpiresAt() : null)
                .lastScanAt(state != null ? state.getLastScanAt() : null)
                .moodScore(moodScore)
                .sampleCount(state != null ? state.getSampleCount() : 0)
                .season(resolveSeason(state))
                .weather(weather)
                .timePhase(computeTimePhase(tzOffsetMinutes))
                .specialEvent(specialEvent)
                .displayEnv(computeDisplayEnv(environment, weather, specialEvent))
                .build();
    }

    /** 季节读取：优先 state.season（每日落库），NULL 时实时计算兜底（Sprint 2.1 行为） */
    private TreeSeason resolveSeason(WishWorldTreeState state) {
        return state != null && state.getSeason() != null
                ? state.getSeason()
                : currentSeason();
    }

    /** 当前季节（UTC 日期判定，与 V10 回填/TreeSeason 契约一致） */
    private TreeSeason currentSeason() {
        return TreeSeason.from(LocalDate.now(ZoneOffset.UTC));
    }

    /** 时段：按客户端时区偏移计算本地时刻（文档验收：按用户本地时区非服务器时区） */
    private TreeTimePhase computeTimePhase(Integer tzOffsetMinutes) {
        int offset = 0;
        if (tzOffsetMinutes != null && Math.abs(tzOffsetMinutes) <= MAX_TZ_OFFSET_MINUTES) {
            offset = tzOffsetMinutes;
        }
        LocalTime localTime = LocalTime.now(ZoneOffset.UTC).plusMinutes(offset);
        return TreeTimePhase.from(localTime);
    }

    /**
     * 聚合展示环境：特殊事件 &gt; 情绪 RAINBOW/RAIN &gt; 真实天气。
     * 情绪 SUNNY 为默认态不覆盖真实天气；RAINBOW（情绪彩虹）优先于
     * 真实天气（治愈叙事："收到他人祝福触发彩虹"即时全站可见）。
     */
    private String computeDisplayEnv(TreeEnvironment environment, TreeWeather weather,
                                     SpecialEventVO specialEvent) {
        if (specialEvent != null) {
            return specialEvent.eventCode();
        }
        if (environment == TreeEnvironment.RAINBOW) {
            return TreeEnvironment.RAINBOW.name();
        }
        if (environment == TreeEnvironment.RAIN) {
            return TreeEnvironment.RAIN.name();
        }
        return weather.name();
    }

    // ---------------- 情绪扫描链路（Sprint 1.5 既有逻辑） ----------------

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
        initial.setSeason(currentSeason());
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
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("生命树情绪缓存写入失败（Fail-Open，下次扫描重写）: {}", ex.getMessage());
        }
    }

    private MoodCacheValue readMoodCache() {
        try {
            String json = redisTemplate.opsForValue().get(MOOD_CACHE_KEY);
            return json != null ? objectMapper.readValue(json, MoodCacheValue.class) : null;
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("生命树情绪缓存读取失败（降级返回 null）: {}", ex.getMessage());
            return null;
        }
    }

    private boolean tryAcquireScanLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    SCAN_LOCK_KEY, "1", props.getScanLockTtlSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(locked);
        } catch (DataAccessException ex) {
            // Redis 不可用时仍执行扫描（无锁保护可容忍：单行幂等写入）
            log.warn("生命树扫描锁获取失败，Redis 不可用，无锁继续扫描（Fail-Open）: {}", ex.getMessage());
            return true;
        }
    }

    private void releaseScanLock() {
        try {
            redisTemplate.delete(SCAN_LOCK_KEY);
        } catch (DataAccessException ex) {
            log.warn("生命树扫描锁释放失败（TTL 兜底自动过期）: {}", ex.getMessage());
        }
    }

    // ---------------- VO 组装 ----------------

    private SpecialEventVO toEventVO(WishSpecialEvent event) {
        return TreeEnvAssembler.toEventVO(event);
    }

    private EnvConfigVO toConfigVO(WishEnvConfig config) {
        return TreeEnvAssembler.toConfigVO(config, objectMapper);
    }

    /** Redis 聚合分数缓存结构（不落库，文档 2.2 隐私约束） */
    record MoodCacheValue(Double score, String computedAt, int sampleCount) {}
}
