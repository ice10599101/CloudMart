package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.config.SoftwareProperties;
import com.cloudmart.software.repository.SoftwareRankingMapper;
import com.cloudmart.software.service.SoftwareQueryService;
import com.cloudmart.software.service.SoftwareRankingService;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 榜单实现（方案 4.5/9.2，P1）。
 *
 * <p>口径：access=最近 7 天获取链接去重人数；favorite=最近 7 天新增收藏；
 * rating=贝叶斯平滑且至少 5 人评分（C 为站内总体均值，冷启动 3.5）。</p>
 *
 * <p>性能：聚合结果（前 100 个 ID）缓存到 Redis（TTL 10 分钟）——不得每请求扫描
 * 全量日事实；展示前对缓存 ID 重新执行实时公开可见谓词（下架即时消失，A26）。
 * Redis 不可用时降级为直接聚合（fail-open，仅放大库压力）。</p>
 *
 * <p>开关：P1 榜单独立开关（手册 11.2/15.2「P1 订阅和榜单单独开关」）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareRankingServiceImpl implements SoftwareRankingService {

    /** 榜单候选窗口：前 N 名参与分页 */
    private static final int CANDIDATE_LIMIT = 100;
    /** 缓存 TTL：10 分钟（口径为"最近 7 天"，分钟级延迟可接受） */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY_PREFIX = "software:rank:";

    private final SoftwareRankingMapper rankingMapper;
    private final SoftwareProperties softwareProperties;
    private final SoftwareQueryService queryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Page<SoftwareCardVO> rankings(String type, int page, int size) {
        // 11.2：P1 榜单独立开关，服务端生效（不能仅隐藏前端入口）
        if (!softwareProperties.isRankingsEnabled()) {
            throw new BusinessException("SOFTWARE_RANKINGS_DISABLED", "排行榜暂未开放");
        }
        String normalized = switch (type == null ? "" : type) {
            case "favorite", "rating" -> type;
            default -> "access";
        };
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<Long> rankedIds = rankedIds(normalized);
        int from = Math.min((safePage - 1) * safeSize, rankedIds.size());
        int to = Math.min(from + safeSize, rankedIds.size());
        List<Long> slice = rankedIds.subList(from, to);

        // 实时可见性过滤：缓存中的软件也即时过滤，不展示下架内容（A26）
        Map<Long, SoftwareCardVO> cardsById = queryService.cardsByIds(slice).stream()
                .collect(Collectors.toMap(SoftwareCardVO::id, Function.identity()));
        List<SoftwareCardVO> records = slice.stream()
                .map(cardsById::get)
                .filter(Objects::nonNull)
                .toList();
        Page<SoftwareCardVO> result = new Page<>(safePage, safeSize, rankedIds.size());
        result.setRecords(records);
        return result;
    }

    /** 榜单 ID（缓存优先） */
    private List<Long> rankedIds(String type) {
        String key = CACHE_KEY_PREFIX + type + ":v1";
        List<Long> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = aggregate(type);
        writeCache(key, ids);
        return ids;
    }

    private List<Long> aggregate(String type) {
        return switch (type) {
            case "favorite" -> rankingMapper.topIdsByFavorite7d(
                    LocalDateTime.now(clock).minusDays(7), CANDIDATE_LIMIT);
            case "rating" -> {
                Double overall = rankingMapper.overallRatingMean();
                yield rankingMapper.topIdsByRating(overall == null ? 3.5 : overall,
                        CANDIDATE_LIMIT);
            }
            default -> rankingMapper.topIdsByAccess7d(
                    LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Shanghai")))
                            .minusDays(6), CANDIDATE_LIMIT);
        };
    }

    private List<Long> readCache(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            Long[] ids = objectMapper.readValue(raw, Long[].class);
            return List.of(ids);
        } catch (Exception e) {
            log.warn("榜单缓存读取失败 key={}: {}（降级为直接聚合）", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<Long> ids) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(ids), CACHE_TTL);
        } catch (Exception e) {
            log.warn("榜单缓存写入失败 key={}: {}（忽略，下次请求重算）", key, e.getMessage());
        }
    }
}
