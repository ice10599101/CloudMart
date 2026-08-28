package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.MatchConfig;
import com.cloudmart.wish.repository.MatchConfigMapper;
import com.cloudmart.wish.service.MatchConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 匹配算法配置服务实现（Sprint 2.6）。
 *
 * <p>与 AiConfigServiceImpl 同模式：60s 快照缓存（多节点最迟 1 分钟生效）+
 * 更新即失效；读写异常 Fail-Open 回退默认值（配置属优化参数，
 * 不阻断匹配主链路）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchConfigServiceImpl implements MatchConfigService {

    /** 缓存 TTL：管理端修改后其他节点最迟 1 分钟生效 */
    private static final long CACHE_TTL_MS = 60_000L;

    private final MatchConfigMapper configMapper;

    /** key → (value, loadedAt) */
    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    private record CachedConfig(String value, long loadedAt) {
    }

    @Override
    public double getDoubleConfig(String key, double defaultValue) {
        String value = getStringConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("匹配配置值非数字，使用默认值, key={}, value={}, default={}", key, value, defaultValue);
            return defaultValue;
        }
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
            log.warn("匹配配置值非整数，使用默认值, key={}, value={}, default={}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public List<MatchConfig> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<MatchConfig>()
                .orderByAsc(MatchConfig::getConfigKey));
    }

    @Override
    @Transactional
    public MatchConfig updateConfig(String configKey, String configValue, Long adminUserId) {
        validateConfigValue(configKey, configValue);
        MatchConfig config = configMapper.selectOne(new LambdaQueryWrapper<MatchConfig>()
                .eq(MatchConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "配置键不存在: " + configKey);
        }
        MatchConfig update = new MatchConfig();
        update.setId(config.getId());
        update.setConfigValue(configValue);
        update.setUpdatedBy(adminUserId);
        configMapper.updateById(update);
        // 回填缓存（而非仅 remove）：remove 后 loadCache 命中未过期快照缺键，
        // 会错误回退默认值导致"实时生效"失效
        configCache.put(configKey, new CachedConfig(configValue, System.currentTimeMillis()));
        log.info("更新匹配配置, key={}, value={}, adminUserId={}", configKey, configValue, adminUserId);
        MatchConfig fresh = configMapper.selectById(config.getId());
        return fresh != null ? fresh : config;
    }

    /**
     * 配置值校验：权重键 0-1；其余键非负整数（WISH_MATCH_CONFIG_INVALID）。
     */
    private void validateConfigValue(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_MATCH_CONFIG_INVALID, "配置键/值不能为空");
        }
        try {
            if (key.startsWith("match.weight_")) {
                double v = Double.parseDouble(value.trim());
                if (v < 0 || v > 1) {
                    throw new BusinessException(WishErrorCodes.WISH_MATCH_CONFIG_INVALID,
                            "权重须在 0-1 之间: " + key);
                }
            } else {
                int v = Integer.parseInt(value.trim());
                if (v < 0) {
                    throw new BusinessException(WishErrorCodes.WISH_MATCH_CONFIG_INVALID,
                            "配置值须为非负整数: " + key);
                }
            }
        } catch (NumberFormatException ex) {
            throw new BusinessException(WishErrorCodes.WISH_MATCH_CONFIG_INVALID,
                    "配置值格式非法: " + key);
        }
    }

    private String getStringConfig(String key, String defaultValue) {
        CachedConfig cached = loadCache().get(key);
        return cached != null ? cached.value() : defaultValue;
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
            List<MatchConfig> configs = configMapper.selectList(null);
            long loadedAt = System.currentTimeMillis();
            Map<String, CachedConfig> fresh = configs.stream()
                    .collect(Collectors.toMap(MatchConfig::getConfigKey,
                            c -> new CachedConfig(c.getConfigValue(), loadedAt),
                            (a, b) -> a));
            configCache.clear();
            configCache.putAll(fresh);
        } catch (DataAccessException ex) {
            log.warn("匹配配置加载失败，沿用现有缓存（Fail-Open）: {}", ex.getMessage());
        }
    }
}
