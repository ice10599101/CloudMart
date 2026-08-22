package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.TreeFruitsQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
import com.cloudmart.wish.service.TreeEnvService;
import com.cloudmart.wish.service.WorldTreeService;
import com.cloudmart.wish.service.impl.TreeBoundsParser.TreeBounds;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.cloudmart.wish.vo.TreeFruitVO;
import com.cloudmart.wish.vo.TreePositionVO;
import com.cloudmart.wish.vo.WorldTreeVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 世界生命树聚合服务实现（Sprint 2.1，文档 2.5 / 第二章 1.）。
 *
 * <p><b>上树口径</b>（聚合与果实分页共用，与公开列表 {@code listWishes} 一致，
 * 保证四端展示与心愿广场数据对齐）：visibility=PUBLIC + audit_status=APPROVED
 * + is_visible=1 + status ∈ (ACTIVE/FULFILLING/FULFILLED) + 未软删。</p>
 *
 * <p><b>Redis 策略</b>（AGENTS.md 14.3/20 + 文档 26 章缓存策略）：</p>
 * <ul>
 *   <li>聚合计数缓存 {@code wish:tree:aggregation}（TTL 5 分钟 + ±30s 随机
 *       抖动防集中过期）；仅缓存 DB 计数三值，environment/season 实时读取
 *       ——环境（情绪联动）变化即时反映，计数允许 ≤5 分钟延迟（展示性数据）</li>
 *   <li>防击穿：miss 时 SETNX 短锁（TTL 5s），抢到锁者回源；未抢到者等待
 *       50ms 重读缓存（锁持有者通常 50ms 内完成回填），重读仍 miss 直查 DB
 *       保底（可用性优先于击穿保护）</li>
 *   <li>Fail-Open：Redis 读/写异常均不阻塞——读降级 null、写仅告警；
 *       Redis 全挂时所有请求直查 DB（3 个走索引的聚合，当前量级可承受）</li>
 * </ul>
 *
 * <p><b>写路径不主动失效缓存</b>：心愿过审/还愿等计数变化由 TTL 5 分钟自然
 * 收敛（展示性数据允许延迟）；四端读同一缓存 key 保证跨端一致
 * （文档跨端一致性验收）。后续如需秒级一致再挂 DEL 钩子。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldTreeServiceImpl implements WorldTreeService {

    /** 聚合计数缓存 Key（public 供集成测试断言/清理） */
    public static final String AGG_CACHE_KEY = "wish:tree:aggregation";
    /** 聚合回源互斥锁 Key（public 供集成测试构造占用态） */
    public static final String AGG_LOCK_KEY = "wish:tree:aggregation:lock";

    /** 计数缓存 TTL 基准（文档 26 章：世界树聚合数据 TTL 5min） */
    private static final long CACHE_TTL_MINUTES = 5;
    /** TTL 随机抖动上下限（秒，防集中过期） */
    private static final long CACHE_JITTER_SECONDS = 30;
    /** 防击穿锁 TTL（秒，略大于单次聚合查询耗时） */
    private static final long LOCK_TTL_SECONDS = 5;
    /** 锁未抢到时的等待重读间隔（毫秒，锁持有者通常一个聚合查询内完成回填） */
    private static final long LOCK_WAIT_MS = 50;

    private final WishMapper wishMapper;
    private final WishWorldTreeStateMapper stateMapper;
    private final UserFeignClient userFeignClient;
    private final QWeatherClient weatherClient;
    private final TreeEnvService treeEnvService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public WorldTreeVO getTreeAggregation() {
        AggregateCounts counts = readCountsCache();
        if (counts == null) {
            counts = loadCountsWithStampedeProtection();
        }
        // 环境/季节/天气/特殊事件实时读取：不受计数缓存 5 分钟延迟影响
        // （情绪联动分钟级变化；天气自带 5 分钟 Redis 缓存；事件为索引查询）
        WishWorldTreeState state = stateMapper.selectById(WishWorldTreeState.SINGLETON_ID);
        TreeEnvironment environment = state != null ? state.getEnvironment() : TreeEnvironment.SUNNY;
        // 季节优先读 state.season（Sprint 2.2 每日落库）；NULL 时实时计算兜底
        TreeSeason season = state != null && state.getSeason() != null
                ? state.getSeason()
                : TreeSeason.from(LocalDate.now(ZoneOffset.UTC));
        SpecialEventVO specialEvent = treeEnvService.getActiveSpecialEvent();
        return new WorldTreeVO(
                counts.totalFruits(), counts.totalBloom(), counts.totalLight(),
                environment, season, weatherClient.getCurrentWeather(), specialEvent,
                state != null ? state.getTriggeredAt() : null);
    }

    @Override
    public FruitPage listFruits(TreeFruitsQuery query) {
        Long cursor = parseCursor(query.cursor());
        Optional<TreeBounds> bounds = TreeBoundsParser.parse(
                query.minLat(), query.maxLat(), query.minLng(), query.maxLng());
        int fetchSize = query.pageSize() + 1; // 多取 1 条判断 hasMore

        LambdaQueryWrapper<Wish> wrapper = new LambdaQueryWrapper<Wish>()
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true)
                .in(Wish::getStatus, WishStatus.ACTIVE, WishStatus.FULFILLING, WishStatus.FULFILLED)
                .isNotNull(Wish::getTreeTheta)
                .orderByDesc(Wish::getId)
                .last("LIMIT " + fetchSize);
        if (cursor != null) {
            wrapper.lt(Wish::getId, cursor);
        }
        bounds.ifPresent(b -> applyBoundsFilter(wrapper, b));

        List<Wish> wishes = wishMapper.selectList(wrapper);
        boolean hasMore = wishes.size() > query.pageSize();
        if (hasMore) {
            wishes = wishes.subList(0, query.pageSize());
        }
        if (wishes.isEmpty()) {
            return new FruitPage(Collections.emptyList(), null, false);
        }

        Map<Long, String> nicknameMap = fetchAuthorNicknames(
                wishes.stream().map(Wish::getUserId).collect(Collectors.toSet()));
        List<TreeFruitVO> fruits = wishes.stream()
                .map(w -> toFruitVO(w, nicknameMap))
                .toList();
        String nextCursor = hasMore ? String.valueOf(wishes.get(wishes.size() - 1).getId()) : null;
        return new FruitPage(fruits, nextCursor, hasMore);
    }

    /** 应用 bounds 视口过滤（phi 不环绕直接 BETWEEN；theta 环绕窗口拆 OR） */
    private void applyBoundsFilter(LambdaQueryWrapper<Wish> wrapper, TreeBounds bounds) {
        wrapper.between(Wish::getTreePhi, bounds.minPhi(), bounds.maxPhi());
        if (bounds.wrapTheta()) {
            // 跨 0/2π 经度环绕：theta ≥ minTheta OR theta ≤ maxTheta
            wrapper.and(w -> w.ge(Wish::getTreeTheta, bounds.minTheta())
                    .or().le(Wish::getTreeTheta, bounds.maxTheta()));
        } else {
            wrapper.between(Wish::getTreeTheta, bounds.minTheta(), bounds.maxTheta());
        }
    }

    private TreeFruitVO toFruitVO(Wish wish, Map<Long, String> nicknameMap) {
        return new TreeFruitVO(
                wish.getId(),
                wish.getTitle(),
                wish.getFruitType(),
                nicknameMap.getOrDefault(wish.getUserId(), "心愿旅人"),
                wish.getLightCount(),
                new TreePositionVO(
                        wish.getTreeTheta().doubleValue(),
                        wish.getTreePhi().doubleValue()));
    }

    // ---------------- 聚合计数：DB 查询 ----------------

    /**
     * 单条 SQL 聚合三值（一次往返、同口径原子快照）；
     * MyBatis-Plus @TableLogic 自动追加 deleted_at IS NULL。
     */
    private AggregateCounts queryCounts() {
        QueryWrapper<Wish> wrapper = new QueryWrapper<Wish>()
                .select("COUNT(*) AS total_fruits",
                        "SUM(CASE WHEN fruit_type = 'BLOOM' THEN 1 ELSE 0 END) AS total_bloom",
                        "IFNULL(SUM(light_count), 0) AS total_light")
                .eq("visibility", WishVisibility.PUBLIC.name())
                .eq("audit_status", AuditStatus.APPROVED.name())
                .eq("is_visible", 1)
                // 与 listFruits 同口径：仅统计已固化坐标（在树上）的果实，
                // 无坐标的历史脏数据不计入（theta 固化即上树）
                .isNotNull("tree_theta")
                .in("status", WishStatus.ACTIVE.name(),
                        WishStatus.FULFILLING.name(), WishStatus.FULFILLED.name());
        List<Map<String, Object>> rows = wishMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return new AggregateCounts(0, 0, 0);
        }
        Map<String, Object> row = rows.get(0);
        return new AggregateCounts(
                asLong(row.get("total_fruits")),
                asLong(row.get("total_bloom")),
                asLong(row.get("total_light")));
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    // ---------------- 聚合计数：缓存 + 防击穿 ----------------

    private AggregateCounts readCountsCache() {
        try {
            String json = redisTemplate.opsForValue().get(AGG_CACHE_KEY);
            return json != null ? objectMapper.readValue(json, AggregateCounts.class) : null;
        } catch (DataAccessException | JsonProcessingException ex) {
            // Fail-Open：脏数据/连接异常/命令超时均降级为 miss（回源 DB），读失败绝不阻塞业务。
            // DataAccessException 涵盖 RedisConnectionFailureException 与命令超时的 QueryTimeoutException
            log.warn("世界树聚合缓存读取失败（Fail-Open 降级回源）: {}", ex.getMessage());
            return null;
        }
    }

    private void writeCountsCache(AggregateCounts counts) {
        try {
            long ttlSeconds = TimeUnit.MINUTES.toSeconds(CACHE_TTL_MINUTES)
                    + ThreadLocalRandom.current().nextLong(-CACHE_JITTER_SECONDS, CACHE_JITTER_SECONDS + 1);
            redisTemplate.opsForValue().set(AGG_CACHE_KEY,
                    objectMapper.writeValueAsString(counts),
                    Duration.ofSeconds(Math.max(ttlSeconds, CACHE_JITTER_SECONDS)));
        } catch (DataAccessException | JsonProcessingException ex) {
            // Fail-Open：回填失败仅告警（下次读取仍回源），不影响响应
            log.warn("世界树聚合缓存写入失败（Fail-Open 仅告警）: {}", ex.getMessage());
        }
    }

    /**
     * 防击穿回源：抢锁者查 DB 后回填缓存；未抢到者等待 50ms 重读缓存
     * （锁持有者通常已完成回填），重读仍 miss 则直查 DB 保底（可用性优先）。
     * 回填职责仅在「查 DB」路径执行——等待/双检命中他方回填值时不重复写
     * （避免冗余 SET 刷新 TTL）。
     */
    private AggregateCounts loadCountsWithStampedeProtection() {
        if (!tryAcquireLock()) {
            AggregateCounts counts = waitForOtherInstanceFill();
            if (counts != null) {
                return counts;
            }
            log.info("世界树聚合防击穿等待超时，直查 DB 保底");
            AggregateCounts fallback = queryCounts();
            writeCountsCache(fallback);
            return fallback;
        }
        try {
            // 双重检查：排队期间锁持有者可能已完成回填
            AggregateCounts counts = readCountsCache();
            if (counts != null) {
                return counts;
            }
            AggregateCounts loaded = queryCounts();
            writeCountsCache(loaded);
            return loaded;
        } finally {
            releaseLock();
        }
    }

    private boolean tryAcquireLock() {
        try {
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(AGG_LOCK_KEY, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(locked);
        } catch (DataAccessException ex) {
            // Redis 不可用/命令超时时无锁继续（单条聚合查询幂等，无一致性风险）
            log.warn("世界树聚合锁获取失败，Redis 不可用，无锁回源（Fail-Open）: {}", ex.getMessage());
            return true;
        }
    }

    private void releaseLock() {
        try {
            redisTemplate.delete(AGG_LOCK_KEY);
        } catch (DataAccessException ex) {
            log.warn("世界树聚合锁释放失败（TTL 兜底自动过期）: {}", ex.getMessage());
        }
    }

    private AggregateCounts waitForOtherInstanceFill() {
        try {
            Thread.sleep(LOCK_WAIT_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
        return readCountsCache();
    }

    // ---------------- 作者昵称（与 WishServiceImpl.fetchAuthorInfo 同源模式） ----------------

    /** 批量解析作者昵称（Fail-Open：Feign 失败返回空 Map，调用方降级占位昵称） */
    private Map<Long, String> fetchAuthorNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .filter(m -> m.get("id") instanceof Number)
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> (String) m.getOrDefault("nickname", "心愿旅人"),
                                (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量获取果实作者昵称失败，降级为占位昵称: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }

    /** 聚合计数三值（缓存载体，与 DB 聚合口径一致） */
    record AggregateCounts(long totalFruits, long totalBloom, long totalLight) {
    }
}
