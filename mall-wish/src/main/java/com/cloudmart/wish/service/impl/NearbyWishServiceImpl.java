package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishMapProperties;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.NearbyWishService;
import com.cloudmart.wish.util.GeoHashUtils;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.NearbyWishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LBS 附近心愿服务实现（Sprint 3.1，文档 2.10/3.1）。
 *
 * <p>查询窗口：lat/lng → geohash5（约 4.9km 格）+ 8 邻格前缀匹配
 * （DB 仅存 geohash7 无坐标列，范围过滤按 geohash 前缀 + 内存 Haversine
 * 距离二次裁剪——隐私验收：DB 仅有 geohash 字段无 lat/lng 列）。</p>
 *
 * <p>缓存：map:nearby:{geohash5}:{radius} / map:cluster:{geohash5}:{radius}，
 * TTL 5min + 随机抖动（文档：聚合策略 Redis + TTL 5min + 随机抖动）；
 * Redis 异常 Fail-Open 直查 DB。</p>
 *
 * <p>降级链：radius 异常（null/0/负数/超 50km）→ 默认 5km（验收）；
 * 空坐标（null/0,0）→ 默认城市中心兜底（验收：避免空白页）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NearbyWishServiceImpl implements NearbyWishService {

    private static final int DEFAULT_RADIUS_M = 5000;
    private static final int MAX_RADIUS_M = 50000;
    /** 查询窗口精度（geohash5 ≈ 4.9km 格，配合 8 邻格覆盖 5-50km 半径） */
    private static final int QUERY_PRECISION = 5;
    /** 聚合网格精度（geohash6 ≈ 1.2km，文档 2.10） */
    private static final int CLUSTER_PRECISION = 6;

    private static final String NEARBY_CACHE_PREFIX = "map:nearby:";
    private static final String CLUSTER_CACHE_PREFIX = "map:cluster:";
    private static final ObjectMapper CACHE_MAPPER = new ObjectMapper();
    private static final TypeReference<List<NearbyWishVO>> VO_LIST_TYPE = new TypeReference<>() {
    };

    private final WishMapper wishMapper;
    private final WishMapProperties mapProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<NearbyWishVO> nearby(Long userId, Double lat, Double lng, Integer radius, String geohash) {
        double[] center = resolveCenter(lat, lng, geohash);
        int radiusM = resolveRadius(radius);
        String cacheKey = NEARBY_CACHE_PREFIX + GeoHashUtils.encode(center[0], center[1], QUERY_PRECISION)
                + ":" + radiusM + ":" + (geohash == null ? "c" : "g");
        List<NearbyWishVO> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<NearbyWishVO> result = queryNearby(center, radiusM, geohash);
        writeCache(cacheKey, result);
        return result;
    }

    @Override
    public List<MapClusterVO> cluster(Long userId, Double lat, Double lng, Integer radius, String geohash) {
        double[] center = resolveCenter(lat, lng, geohash);
        int radiusM = resolveRadius(radius);
        String cacheKey = CLUSTER_CACHE_PREFIX + GeoHashUtils.encode(center[0], center[1], QUERY_PRECISION)
                + ":" + radiusM + ":" + (geohash == null ? "c" : "g");
        List<NearbyWishVO> cached = readCache(cacheKey);
        if (cached != null) {
            return aggregate(cached);
        }
        List<NearbyWishVO> result = queryNearby(center, radiusM, geohash);
        writeCache(cacheKey, result);
        return aggregate(result);
    }

    /** DB 查询（geohash 前缀 9 格窗口）+ 内存距离裁剪 + 模糊坐标组装 */
    private List<NearbyWishVO> queryNearby(double[] center, int radiusM, String geohash) {
        // 查询前缀集合：geohash 参数直取其 5 位前缀邻格；lat/lng 场景同构
        String centerCell = GeoHashUtils.encode(center[0], center[1], QUERY_PRECISION);
        Set<String> prefixCells = new java.util.LinkedHashSet<>(GeoHashUtils.neighbors(centerCell));

        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true)
                .isNotNull(Wish::getGeohash)
                .and(q -> {
                    for (String prefix : prefixCells) {
                        q.or(w -> w.likeRight(Wish::getGeohash, prefix));
                    }
                })
                .last("LIMIT " + mapProperties.getMaxResults()));

        double queryLat = center[0];
        double queryLng = center[1];
        List<NearbyWishVO> result = new ArrayList<>();
        for (Wish wish : wishes) {
            String wishGeohash = wish.getGeohash();
            // 防御校验（验收：geohash 长度<6 或非法字符 → 拒绝该条而非整查询）
            if (wishGeohash == null || wishGeohash.length() < 6 || !isValidGeohash(wishGeohash)) {
                continue;
            }
            double[] cellCenter = GeoHashUtils.decodeCenter(wishGeohash);
            double distance = GeoHashUtils.distanceMeters(queryLat, queryLng, cellCenter[0], cellCenter[1]);
            if (distance > radiusM) {
                continue;
            }
            // 模糊坐标：geohash7 网格中心 + wishId 种子确定性偏移（0-50m，可复现）
            double[] offset = GeoHashUtils.deterministicOffset(cellCenter[0], cellCenter[1], wish.getId());
            result.add(new NearbyWishVO(
                    wish.getId(),
                    wish.getTitle(),
                    wish.getFruitType() == null ? null : wish.getFruitType().name(),
                    round6(offset[0]),
                    round6(offset[1]),
                    (int) Math.round(distance),
                    wish.getLightCount(),
                    wishGeohash.substring(0, 6),
                    wish.getCreatedAt()));
        }
        result.sort(Comparator.comparingInt(NearbyWishVO::distance));
        return result;
    }

    /** geohash6 网格聚合（数量角标；坐标=网格中心，不返回单点） */
    private List<MapClusterVO> aggregate(List<NearbyWishVO> wishes) {
        Map<String, List<NearbyWishVO>> byGrid = new HashMap<>();
        for (NearbyWishVO wish : wishes) {
            if (wish.geohash() != null && wish.geohash().length() >= CLUSTER_PRECISION) {
                byGrid.computeIfAbsent(wish.geohash().substring(0, CLUSTER_PRECISION), k -> new ArrayList<>())
                        .add(wish);
            }
        }
        List<MapClusterVO> clusters = new ArrayList<>();
        for (Map.Entry<String, List<NearbyWishVO>> entry : byGrid.entrySet()) {
            double[] center = GeoHashUtils.decodeCenter(entry.getKey());
            clusters.add(new MapClusterVO(entry.getKey(), round6(center[0]), round6(center[1]),
                    entry.getValue().size()));
        }
        clusters.sort(Comparator.comparingInt(MapClusterVO::count).reversed());
        return clusters;
    }

    /** 解析查询中心：geohash 参数优先；空坐标（null/0,0）→ 默认城市兜底 */
    private double[] resolveCenter(Double lat, Double lng, String geohash) {
        if (geohash != null && !geohash.isBlank()) {
            try {
                GeoHashUtils.validate(geohash, 6);
                return GeoHashUtils.decodeCenter(geohash);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, ex.getMessage());
            }
        }
        if (isBlankCoordinate(lat, lng)) {
            // 空坐标兜底：默认城市中心（验收：避免空白页）
            return new double[]{mapProperties.getDefaultLat(), mapProperties.getDefaultLng()};
        }
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "坐标越界");
        }
        return new double[]{lat, lng};
    }

    /** radius 兜底：null/<=0/>50000 → 默认 5km（验收） */
    private int resolveRadius(Integer radius) {
        if (radius == null || radius <= 0 || radius > MAX_RADIUS_M) {
            return DEFAULT_RADIUS_M;
        }
        return radius;
    }

    private boolean isBlankCoordinate(Double lat, Double lng) {
        return lat == null || lng == null
                || (lat == 0.0 && lng == 0.0)
                || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0;
    }

    private boolean isValidGeohash(String geohash) {
        try {
            GeoHashUtils.validate(geohash, 6);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 缓存读（Fail-Open：异常当未命中） */
    private List<NearbyWishVO> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return CACHE_MAPPER.readValue(json, VO_LIST_TYPE);
            }
        } catch (DataAccessException ex) {
            log.warn("附近心愿缓存读取失败（Fail-Open 直查 DB）: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("附近心愿缓存反序列化失败，视为未命中: {}", ex.getMessage());
        }
        return null;
    }

    /** 缓存写：TTL 5min 基础 + 0-60s 随机抖动（文档：TTL 5min + 随机抖动）；异常 Fail-Open */
    private void writeCache(String key, List<NearbyWishVO> result) {
        try {
            long ttl = mapProperties.getCacheTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 60);
            redisTemplate.opsForValue().set(key, CACHE_MAPPER.writeValueAsString(result),
                    Duration.ofSeconds(ttl));
        } catch (DataAccessException ex) {
            log.warn("附近心愿缓存写入失败（Fail-Open）: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("附近心愿缓存序列化失败（Fail-Open）: {}", ex.getMessage());
        }
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
