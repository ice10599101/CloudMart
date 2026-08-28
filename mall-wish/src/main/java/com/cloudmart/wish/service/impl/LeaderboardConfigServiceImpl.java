package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.LeaderboardConfig;
import com.cloudmart.wish.repository.LeaderboardConfigMapper;
import com.cloudmart.wish.service.LeaderboardConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 排行榜配置服务实现（Sprint 2.7）。
 *
 * <p>60s 快照缓存 + 更新回填实时生效（吸取 MatchConfig 教训：
 * remove 后未过期快照缺键会错误回退默认值，必须回填）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardConfigServiceImpl implements LeaderboardConfigService {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final Set<String> VALID_KEYS = Set.of(
            "lb.refresh_minutes", "lb.top_size", "lb.tiebreak", "lb.exclude_restricted");

    private final LeaderboardConfigMapper configMapper;

    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    private record CachedConfig(String value, long loadedAt) {
    }

    @Override
    public String getStringConfig(String key, String defaultValue) {
        CachedConfig cached = loadCache().get(key);
        return cached != null ? cached.value() : defaultValue;
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        String value = getStringConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("排行榜配置值非整数，使用默认值, key={}, value={}", key, value);
            return defaultValue;
        }
    }

    @Override
    public List<LeaderboardConfig> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<LeaderboardConfig>()
                .orderByAsc(LeaderboardConfig::getConfigKey));
    }

    @Override
    @Transactional
    public LeaderboardConfig updateConfig(String configKey, String configValue, Long adminUserId) {
        validateConfigValue(configKey, configValue);
        LeaderboardConfig config = configMapper.selectOne(new LambdaQueryWrapper<LeaderboardConfig>()
                .eq(LeaderboardConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "配置键不存在: " + configKey);
        }
        LeaderboardConfig update = new LeaderboardConfig();
        update.setId(config.getId());
        update.setConfigValue(configValue);
        update.setUpdatedBy(adminUserId);
        configMapper.updateById(update);
        configCache.put(configKey, new CachedConfig(configValue, System.currentTimeMillis()));
        log.info("更新排行榜配置, key={}, value={}, adminUserId={}", configKey, configValue, adminUserId);
        LeaderboardConfig fresh = configMapper.selectById(config.getId());
        return fresh != null ? fresh : config;
    }

    private void validateConfigValue(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID, "配置键/值不能为空");
        }
        if (!VALID_KEYS.contains(key)) {
            throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID, "配置键非法: " + key);
        }
        switch (key) {
            case "lb.refresh_minutes" -> {
                int v = parseInt(key, value);
                if (v < 1 || v > 1440) {
                    throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                            "刷新周期须为 1-1440 分钟");
                }
            }
            case "lb.top_size" -> {
                int v = parseInt(key, value);
                if (v < 10 || v > 100) {
                    throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                            "Top N 须为 10-100");
                }
            }
            case "lb.tiebreak" -> {
                if (!"CREATED_AT_ASC".equalsIgnoreCase(value.trim())
                        && !"CREATED_AT_DESC".equalsIgnoreCase(value.trim())) {
                    throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                            "同分处理仅支持 CREATED_AT_ASC / CREATED_AT_DESC");
                }
            }
            case "lb.exclude_restricted" -> {
                int v = parseInt(key, value);
                if (v != 0 && v != 1) {
                    throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                            "封禁过滤仅支持 0/1");
                }
            }
            default -> throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                    "配置键非法: " + key);
        }
    }

    private int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(WishErrorCodes.WISH_LEADERBOARD_CONFIG_INVALID,
                    "配置值须为整数: " + key);
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
            List<LeaderboardConfig> configs = configMapper.selectList(null);
            long loadedAt = System.currentTimeMillis();
            Map<String, CachedConfig> fresh = configs.stream()
                    .collect(Collectors.toMap(LeaderboardConfig::getConfigKey,
                            c -> new CachedConfig(c.getConfigValue(), loadedAt),
                            (a, b) -> a));
            configCache.clear();
            configCache.putAll(fresh);
        } catch (DataAccessException ex) {
            log.warn("排行榜配置加载失败，沿用现有缓存（Fail-Open）: {}", ex.getMessage());
        }
    }
}
