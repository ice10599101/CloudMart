package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.converter.AdminConverter;
import com.cloudmart.admin.dto.AdminDictTypeRequest;
import com.cloudmart.admin.dto.AdminDictTypeResponse;
import com.cloudmart.admin.entity.AdminDictType;
import com.cloudmart.admin.repository.AdminDictTypeMapper;
import com.cloudmart.admin.service.AdminDictTypeService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdminDictTypeServiceImpl implements AdminDictTypeService {

    private static final String CACHE_PREFIX = "admin:dict_type:";
    private static final int BASE_TTL_SECONDS = 86400;
    private static final int MAX_JITTER_SECONDS = 3600;

    private final AdminDictTypeMapper adminDictTypeMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminConverter adminConverter;

    public AdminDictTypeServiceImpl(AdminDictTypeMapper adminDictTypeMapper,
                                    StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    AdminConverter adminConverter) {
        this.adminDictTypeMapper = adminDictTypeMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.adminConverter = adminConverter;
    }

    @Override
    public List<AdminDictTypeResponse> list() {
        return adminDictTypeMapper.selectList(
                new LambdaQueryWrapper<AdminDictType>().orderByAsc(AdminDictType::getId)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public AdminDictTypeResponse getById(Long id) {
        AdminDictType dictType = adminDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        return toResponse(dictType);
    }

    @Override
    @Transactional
    public void create(AdminDictTypeRequest request) {
        checkDictTypeUnique(request.dictType(), null);

        AdminDictType dictType = new AdminDictType();
        dictType.setDictName(request.dictName());
        dictType.setDictType(request.dictType());
        dictType.setStatus(request.status());
        dictType.setRemark(request.remark());
        adminDictTypeMapper.insert(dictType);

        evictCache(request.dictType());
    }

    @Override
    @Transactional
    public void update(Long id, AdminDictTypeRequest request) {
        AdminDictType dictType = adminDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }

        checkDictTypeUnique(request.dictType(), id);

        String oldDictType = dictType.getDictType();

        dictType.setDictName(request.dictName());
        dictType.setDictType(request.dictType());
        dictType.setStatus(request.status());
        dictType.setRemark(request.remark());
        adminDictTypeMapper.updateById(dictType);

        evictCache(oldDictType);
        if (!oldDictType.equals(request.dictType())) {
            evictCache(request.dictType());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminDictType dictType = adminDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        adminDictTypeMapper.deleteById(id);

        evictCache(dictType.getDictType());
    }

    @Override
    public void refreshCache() {
        List<AdminDictType> allTypes = adminDictTypeMapper.selectList(null);
        for (AdminDictType dictType : allTypes) {
            String cacheKey = CACHE_PREFIX + dictType.getDictType();
            try {
                String json = objectMapper.writeValueAsString(toResponse(dictType));
                Duration ttl = Duration.ofSeconds(BASE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(MAX_JITTER_SECONDS));
                redisTemplate.opsForValue().set(cacheKey, json, ttl);
            } catch (Exception e) {
                throw new BusinessException("CACHE_REFRESH_FAILED", "字典类型缓存刷新失败: " + dictType.getDictType(), e);
            }
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminDictType dictType = adminDictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        dictType.setStatus(status);
        adminDictTypeMapper.updateById(dictType);
        evictCache(dictType.getDictType());
    }

    private void checkDictTypeUnique(String dictType, Long excludeId) {
        LambdaQueryWrapper<AdminDictType> wrapper = new LambdaQueryWrapper<AdminDictType>()
                .eq(AdminDictType::getDictType, dictType);
        AdminDictType existing = adminDictTypeMapper.selectOne(wrapper);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("DICT_TYPE_EXISTS", "字典类型编码已存在");
        }
    }

    private void evictCache(String dictType) {
        redisTemplate.delete(CACHE_PREFIX + dictType);
    }

    private AdminDictTypeResponse toResponse(AdminDictType dictType) {
        return adminConverter.toDictTypeResponse(dictType);
    }
}
