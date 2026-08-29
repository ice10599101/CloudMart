package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.GrayscaleConfig;
import com.cloudmart.wish.repository.GrayscaleConfigMapper;
import com.cloudmart.wish.service.GrayscaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 灰度控制服务实现（Sprint 2.8）。
 *
 * <p>路由：{@link GrayRouter#isHit} 纯函数哈希分流（无状态、跨实例一致，
 * 同一用户恒命中同一档——文档 2.8 验收）；配置 60s 快照缓存 + 更新回填
 * 实时生效（MatchConfig 同款教训：必须回填而非 remove）。
 * 回滚 = 比例置 0，管理端一键操作。</p>
 *
 * <p>Fail-Open：配置读异常时按「全量放行」处理——灰度仅控制新功能可见性，
 * 比误杀全量用户更安全（文档 20：禁止出错后默认继续，此处降级方向明确留档）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrayscaleServiceImpl implements GrayscaleService {

    private static final long CACHE_TTL_MS = 60_000L;

    /** 功能键白名单（代码枚举；文档 2.8：灰度比例 0%/5%/20%/50%/100%） */
    private static final Set<String> FEATURE_KEYS = new HashSet<>(List.of(
            "wish_ai_assistant",
            "wish_tree_hole",
            "wish_time_capsule",
            "wish_match_squad",
            "wish_leaderboard",
            "wish_legacy_flow",
            "wish_world_tree_enhanced",
            "wish_live_widget"));

    private static final Set<Integer> RATIO_LADDER = Set.of(0, 5, 20, 50, 100);

    private final GrayscaleConfigMapper configMapper;

    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    private record CachedConfig(int grayRatio, long loadedAt) {
    }

    @Override
    public boolean isEnabled(Long userId, String featureKey) {
        requireKnownKey(featureKey);
        return GrayRouter.isHit(userId, featureKey, loadRatio(featureKey));
    }

    @Override
    public Map<String, Boolean> flagsOf(Long userId, List<String> featureKeys) {
        List<String> keys = (featureKeys == null || featureKeys.isEmpty())
                ? List.copyOf(FEATURE_KEYS)
                : featureKeys;
        Map<String, Boolean> flags = new HashMap<>();
        for (String key : keys) {
            if (!FEATURE_KEYS.contains(key)) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "未知的功能键: " + key);
            }
            flags.put(key, GrayRouter.isHit(userId, key, loadRatio(key)));
        }
        return flags;
    }

    @Override
    public List<GrayscaleConfig> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<GrayscaleConfig>()
                .orderByAsc(GrayscaleConfig::getFeatureKey));
    }

    @Override
    @Transactional
    public GrayscaleConfig updateRatio(String featureKey, int grayRatio, Long adminUserId) {
        requireKnownKey(featureKey);
        int ratio = snapToLadder(grayRatio);
        GrayscaleConfig config = configMapper.selectOne(new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getFeatureKey, featureKey)
                .last("LIMIT 1"));
        if (config == null) {
            // 种子缺失自愈（与配置表幂等补种策略一致）
            config = new GrayscaleConfig();
            config.setFeatureKey(featureKey);
            config.setGrayRatio(ratio);
            config.setUpdatedBy(adminUserId);
            configMapper.insert(config);
        } else {
            GrayscaleConfig update = new GrayscaleConfig();
            update.setId(config.getId());
            update.setGrayRatio(ratio);
            update.setUpdatedBy(adminUserId);
            configMapper.updateById(update);
        }
        configCache.put(featureKey, new CachedConfig(ratio, System.currentTimeMillis()));
        log.info("更新灰度比例, feature={}, ratio={}, adminUserId={}", featureKey, ratio, adminUserId);
        return configMapper.selectOne(new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getFeatureKey, featureKey)
                .last("LIMIT 1"));
    }

    /**
     * 比例吸附到文档档位 {0,5,20,50,100}（越界值取最近档，容错且可解释）。
     */
    private int snapToLadder(int ratio) {
        int clamped = Math.max(0, Math.min(100, ratio));
        return RATIO_LADDER.stream().min(Comparator.comparingInt(l -> Math.abs(l - clamped))).orElse(clamped);
    }

    private void requireKnownKey(String featureKey) {
        if (featureKey == null || !FEATURE_KEYS.contains(featureKey)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "未知的功能键: " + featureKey);
        }
    }

    private int loadRatio(String featureKey) {
        CachedConfig cached = loadCache().get(featureKey);
        if (cached != null) {
            return cached.grayRatio();
        }
        // 缓存缺键（种子缺失/新功能上线）：回源 DB 直查并回填，缺省=全量放行
        try {
            GrayscaleConfig config = configMapper.selectOne(new LambdaQueryWrapper<GrayscaleConfig>()
                    .eq(GrayscaleConfig::getFeatureKey, featureKey)
                    .last("LIMIT 1"));
            int ratio = config != null && config.getGrayRatio() != null ? config.getGrayRatio() : 100;
            configCache.put(featureKey, new CachedConfig(ratio, System.currentTimeMillis()));
            return ratio;
        } catch (DataAccessException ex) {
            log.warn("灰度配置读取失败，降级全量放行（Fail-Open）, feature={}: {}", featureKey, ex.getMessage());
            return 100;
        }
    }

    private Map<String, CachedConfig> loadCache() {
        long now = System.currentTimeMillis();
        boolean expired = configCache.values().stream()
                .anyMatch(c -> now - c.loadedAt() > CACHE_TTL_MS);
        if (configCache.isEmpty() || expired) {
            refreshCache();
        }
        return configCache;
    }

    private void refreshCache() {
        try {
            List<GrayscaleConfig> configs = configMapper.selectList(null);
            long loadedAt = System.currentTimeMillis();
            Map<String, CachedConfig> fresh = configs.stream()
                    .filter(c -> c.getFeatureKey() != null && c.getGrayRatio() != null)
                    .collect(Collectors.toMap(GrayscaleConfig::getFeatureKey,
                            c -> new CachedConfig(c.getGrayRatio(), loadedAt),
                            (a, b) -> a));
            configCache.clear();
            configCache.putAll(fresh);
        } catch (DataAccessException ex) {
            log.warn("灰度配置快照加载失败，沿用现有缓存（Fail-Open）: {}", ex.getMessage());
        }
    }
}
