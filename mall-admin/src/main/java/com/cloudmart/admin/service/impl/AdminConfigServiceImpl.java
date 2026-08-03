package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminConfigRequest;
import com.cloudmart.admin.dto.AdminConfigResponse;
import com.cloudmart.admin.entity.AdminConfig;
import com.cloudmart.admin.repository.AdminConfigMapper;
import com.cloudmart.admin.service.AdminConfigService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdminConfigServiceImpl implements AdminConfigService {

    private static final String CACHE_PREFIX = "admin:config:";
    private static final int BASE_TTL_SECONDS = 86400;
    private static final int MAX_JITTER_SECONDS = 3600;

    private final AdminConfigMapper adminConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AdminConfigServiceImpl(AdminConfigMapper adminConfigMapper,
                                  StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        this.adminConfigMapper = adminConfigMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminConfigResponse> list() {
        return adminConfigMapper.selectList(
                new LambdaQueryWrapper<AdminConfig>().orderByAsc(AdminConfig::getId)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public AdminConfigResponse getById(Long id) {
        AdminConfig config = adminConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("CONFIG_NOT_FOUND", "参数配置不存在");
        }
        return toResponse(config);
    }

    @Override
    public AdminConfigResponse getByKey(String configKey) {
        String cacheKey = CACHE_PREFIX + configKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, AdminConfigResponse.class);
            } catch (Exception e) {
                redisTemplate.delete(cacheKey);
            }
        }

        AdminConfig config = adminConfigMapper.selectOne(
                new LambdaQueryWrapper<AdminConfig>().eq(AdminConfig::getConfigKey, configKey)
        );
        if (config == null) {
            throw new BusinessException("CONFIG_NOT_FOUND", "参数配置不存在");
        }

        AdminConfigResponse response = toResponse(config);
        cacheResult(cacheKey, response);
        return response;
    }

    @Override
    @Transactional
    public void create(AdminConfigRequest request) {
        checkConfigKeyUnique(request.configKey(), null);

        AdminConfig config = new AdminConfig();
        config.setConfigName(request.configName());
        config.setConfigKey(request.configKey());
        config.setConfigValue(request.configValue());
        config.setConfigType(request.configType());
        config.setRemark(request.remark());
        adminConfigMapper.insert(config);

        evictCache(request.configKey());
    }

    @Override
    @Transactional
    public void update(Long id, AdminConfigRequest request) {
        AdminConfig config = adminConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("CONFIG_NOT_FOUND", "参数配置不存在");
        }

        checkConfigKeyUnique(request.configKey(), id);

        String oldConfigKey = config.getConfigKey();

        config.setConfigName(request.configName());
        config.setConfigKey(request.configKey());
        config.setConfigValue(request.configValue());
        config.setConfigType(request.configType());
        config.setRemark(request.remark());
        adminConfigMapper.updateById(config);

        evictCache(oldConfigKey);
        if (!oldConfigKey.equals(request.configKey())) {
            evictCache(request.configKey());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminConfig config = adminConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("CONFIG_NOT_FOUND", "参数配置不存在");
        }
        adminConfigMapper.deleteById(id);

        evictCache(config.getConfigKey());
    }

    @Override
    public void refreshCache() {
        List<AdminConfig> allConfigs = adminConfigMapper.selectList(null);
        for (AdminConfig config : allConfigs) {
            String cacheKey = CACHE_PREFIX + config.getConfigKey();
            try {
                String json = objectMapper.writeValueAsString(toResponse(config));
                Duration ttl = Duration.ofSeconds(BASE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(MAX_JITTER_SECONDS));
                redisTemplate.opsForValue().set(cacheKey, json, ttl);
            } catch (Exception e) {
                throw new BusinessException("CACHE_REFRESH_FAILED", "参数配置缓存刷新失败: " + config.getConfigKey(), e);
            }
        }
    }

    private void checkConfigKeyUnique(String configKey, Long excludeId) {
        LambdaQueryWrapper<AdminConfig> wrapper = new LambdaQueryWrapper<AdminConfig>()
                .eq(AdminConfig::getConfigKey, configKey);
        AdminConfig existing = adminConfigMapper.selectOne(wrapper);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("CONFIG_KEY_EXISTS", "参数键名已存在");
        }
    }

    private void evictCache(String configKey) {
        redisTemplate.delete(CACHE_PREFIX + configKey);
    }

    private void cacheResult(String cacheKey, AdminConfigResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Duration ttl = Duration.ofSeconds(BASE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(MAX_JITTER_SECONDS));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (Exception e) {
            throw new BusinessException("CACHE_WRITE_FAILED", "参数配置缓存写入失败", e);
        }
    }

    private AdminConfigResponse toResponse(AdminConfig config) {
        return new AdminConfigResponse(
                config.getId(),
                config.getConfigName(),
                config.getConfigKey(),
                config.getConfigValue(),
                config.getConfigType(),
                config.getRemark(),
                config.getCreatedAt()
        );
    }
}
