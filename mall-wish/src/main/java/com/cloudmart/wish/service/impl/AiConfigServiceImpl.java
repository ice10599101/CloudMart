package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AiConfigUpdateRequest;
import com.cloudmart.wish.entity.WishAiConfig;
import com.cloudmart.wish.repository.WishAiConfigMapper;
import com.cloudmart.wish.service.AiConfigService;
import com.cloudmart.wish.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI/提醒策略全局配置服务实现（Sprint 2.5）。
 *
 * <p>读取策略：全表加载进内存缓存（配置项 &lt; 20 个，量级可忽略），
 * TTL 60s；管理端更新主动失效——修改后新值即时生效，最迟 1 分钟。</p>
 *
 * <p>降级策略（Fail-Open）：DB 异常时沿用上次缓存或返回调用方默认值，
 * 提醒策略读取失败不阻断通知/报告等主流程。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiConfigServiceImpl implements AiConfigService {

    /** 缓存 TTL：管理端修改后其他节点最迟 1 分钟生效 */
    private static final long CACHE_TTL_MS = 60_000L;

    private final WishAiConfigMapper configMapper;

    /** key → (value, loadedAt)；全量快照缓存 */
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
            log.warn("AI配置值非整数，使用默认值, key={}, value={}, default={}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public List<AiConfigVO> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<WishAiConfig>()
                        .orderByAsc(WishAiConfig::getId))
                .stream().map(AiConfigVO::from).toList();
    }

    @Override
    public AiConfigVO updateConfig(String key, AiConfigUpdateRequest request, Long adminUserId) {
        WishAiConfig config = configMapper.selectOne(new LambdaQueryWrapper<WishAiConfig>()
                .eq(WishAiConfig::getConfigKey, key));
        if (config == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "配置键不存在: " + key);
        }
        config.setConfigValue(request.configValue().trim());
        config.setUpdatedBy(adminUserId);
        configMapper.updateById(config);
        configCache.clear();
        log.info("更新AI配置, key={}, value={}, adminUserId={}", key, request.configValue(), adminUserId);
        return AiConfigVO.from(config);
    }

    /**
     * 加载全量配置缓存；60s 内直接复用，DB 异常沿用上次快照（Fail-Open）。
     */
    private Map<String, CachedConfig> loadCache() {
        long now = Instant.now().toEpochMilli();
        CachedConfig probe = configCache.get(AiConfigService.KEY_REMINDER_DAILY_LIMIT);
        if (probe != null && now - probe.loadedAt() < CACHE_TTL_MS) {
            return configCache;
        }
        try {
            List<WishAiConfig> configs = configMapper.selectList(null);
            Map<String, CachedConfig> snapshot = new ConcurrentHashMap<>();
            for (WishAiConfig config : configs) {
                snapshot.put(config.getConfigKey(), new CachedConfig(config.getConfigValue(), now));
            }
            configCache.clear();
            configCache.putAll(snapshot);
            return configCache;
        } catch (Exception ex) {
            log.warn("AI配置加载失败，沿用上次缓存（Fail-Open）", ex);
            return configCache;
        }
    }
}
