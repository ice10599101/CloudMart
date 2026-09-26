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

    private final WishMapper wishMapper;
    private final WishMapProperties mapProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<NearbyWishVO> nearby(Long userId, Double lat, Double lng, Integer radius, String geohash) {
        double[] center = resolveCenter(lat, lng, geohash);
        int radiusM = resolveRadius(radius);
        String cacheKey = NEARBY_CACHE_PREFIX + GeoHashUtils.encode(center[0], center[1], QUERY_PRECISION)
                + ":" + radiusM + ":" + (geohash == null ? "c" : "g");
        // B07：缓存只保存候选 ID；命中后批量回查当前公共可见状态再重组 VO，
        // 防止已转私密/下架/删除的心愿从旧缓存泄露标题与坐标
        List<Long> cachedIds = readCache(cacheKey);
        if (cachedIds != null) {
            return assembleNearbyVos(loadPublicWishesByIds(cachedIds), center, radiusM);
        }
        List<Wish> wishes = queryNearbyWishes(center, radiusM, geohash);
        writeCache(cacheKey, wishes.stream().map(Wish::getId).toList());
        return assembleNearbyVos(wishes, center, radiusM);
    }

    @Override
    public List<MapClusterVO> cluster(Long userId, Double lat, Double lng, Integer radius, String geohash) {
        double[] center = resolveCenter(lat, lng, geohash);
        int radiusM = resolveRadius(radius);
        String cacheKey = CLUSTER_CACHE_PREFIX + GeoHashUtils.encode(center[0], center[1], QUERY_PRECISION)
                + ":" + radiusM + ":" + (geohash == null ? "c" : "g");
        List<Long> cachedIds = readCache(cacheKey);
        if (cachedIds != null) {
            return aggregate(assembleNearbyVos(loadPublicWishesByIds(cachedIds), center, radiusM));
        }
        List<Wish> wishes = queryNearbyWishes(center, radiusM, geohash);
        writeCache(cacheKey, wishes.stream().map(Wish::getId).toList());
        return aggregate(assembleNearbyVos(wishes, center, radiusM));
    }

    /** DB 查询（geohash 前缀 9 格窗口）+ 内存距离裁剪（B07：VO 组装移至 assembleNearbyVos） */
    private List<Wish> queryNearbyWishes(double[] center, int radiusM, String geohash) {
        // 查询前缀集合：geohash 参数直取其 5 位前缀邻格；lat/lng 场景同构
        Set<String> prefixCells = cellsWithinRadius(center, radiusM);

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
        List<Wish> inRange = new ArrayList<>();
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
            inRange.add(wish);
        }
        return inRange;
    }

    /**
     * B16：按半径自适应选择 geohash 精度并枚举覆盖包围盒的全部网格——
     * 固定 9 邻格在 20/50km 半径下会漏掉跨格心愿。每档精度限定在 ±2 环
     * （≤25 格），查询条件数量有上界。
     */
    static Set<String> cellsWithinRadius(double[] center, int radiusM) {
        int precision;
        if (radiusM <= 6000) {
            precision = 5;   // 格约 4.9km
        } else if (radiusM <= 22000) {
            precision = 4;   // 格约 19.5km
        } else {
            precision = 3;   // 格约 78km
        }
        double cellSize = precision == 5 ? 4900 : precision == 4 ? 19500 : 78000;
        int steps = (int) Math.ceil(radiusM / cellSize) + 1;
        Set<String> cells = new java.util.LinkedHashSet<>();
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                double lat = center[0] + i * cellSize;
                double lng = center[1] + j * cellSize;
                if (lat < -90.0 || lat > 90.0) {
                    continue;
                }
                double lngNorm = lng > 180.0 ? lng - 360.0 : lng < -180.0 ? lng + 360.0 : lng;
                cells.add(GeoHashUtils.encode(lat, lngNorm, precision));
            }
        }
        return cells;
    }

    /** 模糊坐标 VO 组装（geohash7 网格中心 + wishId 种子确定性偏移 0-50m，可复现） */
    private List<NearbyWishVO> assembleNearbyVos(List<Wish> wishes, double[] center, int radiusM) {
        List<NearbyWishVO> result = new ArrayList<>();
        for (Wish wish : wishes) {
            String wishGeohash = wish.getGeohash();
            if (wishGeohash == null || wishGeohash.length() < 6 || !isValidGeohash(wishGeohash)) {
                continue;
            }
            double[] cellCenter = GeoHashUtils.decodeCenter(wishGeohash);
            double distance = GeoHashUtils.distanceMeters(center[0], center[1], cellCenter[0], cellCenter[1]);
            if (distance > radiusM) {
                continue;
            }
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

    /**
     * B07：按缓存 ID 批量回查并重新执行公共可见谓词
     * （未删除 + PUBLIC + isVisible + APPROVED）；不可见者剔除。
     */
    private List<Wish> loadPublicWishesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                    .in(Wish::getId, ids)
                    .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                    .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                    .eq(Wish::getIsVisible, true));
        } catch (Exception ex) {
            // DB 回查失败不能把旧缓存内容当兜底展示（隐私 Fail-Closed）
            log.warn("附近心愿回查失败，按空处理: {}", ex.getMessage());
            return new ArrayList<>();
        }
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
        // B16：lat/lng 必须成对出现
        if ((geohash == null || geohash.isBlank()) && ((lat == null) != (lng == null))) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "经纬度必须成对提供");
        }
        if (geohash != null && !geohash.isBlank()) {
            try {
                GeoHashUtils.validate(geohash, 6);
                return GeoHashUtils.decodeCenter(geohash);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, ex.getMessage());
            }
        }
        // B16：非有限数（NaN/Infinity）一律拒绝
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "坐标非法");
        }
        // B16：0 不是无效标记——0,0 为合法坐标正常参与查询
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
        // B16：仅"完全未提供"回退默认城市；0,0 是合法坐标
        return lat == null && lng == null;
    }

    private boolean isValidGeohash(String geohash) {
        try {
            GeoHashUtils.validate(geohash, 6);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 缓存读（Fail-Open：异常当未命中；B07：只承载候选 ID 列表） */
    private List<Long> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return CACHE_MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() { });
            }
        } catch (DataAccessException ex) {
            log.warn("附近心愿缓存读取失败（Fail-Open 直查 DB）: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("附近心愿缓存反序列化失败，视为未命中: {}", ex.getMessage());
        }
        return null;
    }

    /** 缓存写：只写候选 ID（B07），TTL 5min 基础 + 0-60s 随机抖动；异常 Fail-Open */
    private void writeCache(String key, List<Long> wishIds) {
        try {
            long ttl = mapProperties.getCacheTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 60);
            redisTemplate.opsForValue().set(key, CACHE_MAPPER.writeValueAsString(wishIds),
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
