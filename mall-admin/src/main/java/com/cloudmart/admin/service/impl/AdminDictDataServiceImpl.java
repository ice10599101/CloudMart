package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminDictDataRequest;
import com.cloudmart.admin.dto.AdminDictDataResponse;
import com.cloudmart.admin.entity.AdminDictData;
import com.cloudmart.admin.repository.AdminDictDataMapper;
import com.cloudmart.admin.service.AdminDictDataService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdminDictDataServiceImpl implements AdminDictDataService {

    private static final String CACHE_PREFIX = "admin:dict_data:";
    private static final int BASE_TTL_SECONDS = 86400;
    private static final int MAX_JITTER_SECONDS = 3600;

    private final AdminDictDataMapper adminDictDataMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AdminDictDataServiceImpl(AdminDictDataMapper adminDictDataMapper,
                                    StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper) {
        this.adminDictDataMapper = adminDictDataMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminDictDataResponse> listByType(String dictType) {
        String cacheKey = CACHE_PREFIX + dictType;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<AdminDictDataResponse>>() {});
            } catch (Exception e) {
                redisTemplate.delete(cacheKey);
            }
        }

        List<AdminDictDataResponse> result = adminDictDataMapper.selectList(
                new LambdaQueryWrapper<AdminDictData>()
                        .eq(AdminDictData::getDictType, dictType)
                        .orderByAsc(AdminDictData::getDictSort)
        ).stream().map(this::toResponse).toList();

        cacheResult(cacheKey, result);
        return result;
    }

    @Override
    public AdminDictDataResponse getById(Long id) {
        AdminDictData dictData = adminDictDataMapper.selectById(id);
        if (dictData == null) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        return toResponse(dictData);
    }

    @Override
    @Transactional
    public void create(AdminDictDataRequest request) {
        AdminDictData dictData = new AdminDictData();
        dictData.setDictType(request.dictType());
        dictData.setDictSort(request.dictSort());
        dictData.setDictLabel(request.dictLabel());
        dictData.setDictValue(request.dictValue());
        dictData.setCssClass(request.cssClass());
        dictData.setListClass(request.listClass());
        dictData.setIsDefault(request.isDefault());
        dictData.setStatus(request.status());
        dictData.setRemark(request.remark());
        adminDictDataMapper.insert(dictData);

        evictCache(request.dictType());
    }

    @Override
    @Transactional
    public void update(Long id, AdminDictDataRequest request) {
        AdminDictData dictData = adminDictDataMapper.selectById(id);
        if (dictData == null) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }

        String oldDictType = dictData.getDictType();

        dictData.setDictType(request.dictType());
        dictData.setDictSort(request.dictSort());
        dictData.setDictLabel(request.dictLabel());
        dictData.setDictValue(request.dictValue());
        dictData.setCssClass(request.cssClass());
        dictData.setListClass(request.listClass());
        dictData.setIsDefault(request.isDefault());
        dictData.setStatus(request.status());
        dictData.setRemark(request.remark());
        adminDictDataMapper.updateById(dictData);

        evictCache(oldDictType);
        if (!oldDictType.equals(request.dictType())) {
            evictCache(request.dictType());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminDictData dictData = adminDictDataMapper.selectById(id);
        if (dictData == null) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        adminDictDataMapper.deleteById(id);

        evictCache(dictData.getDictType());
    }

    @Override
    public void refreshCache(String dictType) {
        evictCache(dictType);
        listByType(dictType);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminDictData dictData = adminDictDataMapper.selectById(id);
        if (dictData == null) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        dictData.setStatus(status);
        adminDictDataMapper.updateById(dictData);
        evictCache(dictData.getDictType());
    }

    private void evictCache(String dictType) {
        redisTemplate.delete(CACHE_PREFIX + dictType);
    }

    private void cacheResult(String cacheKey, List<AdminDictDataResponse> result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Duration ttl = Duration.ofSeconds(BASE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(MAX_JITTER_SECONDS));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (Exception e) {
            throw new BusinessException("CACHE_WRITE_FAILED", "字典数据缓存写入失败", e);
        }
    }

    private AdminDictDataResponse toResponse(AdminDictData dictData) {
        return new AdminDictDataResponse(
                dictData.getId(),
                dictData.getDictType(),
                dictData.getDictSort(),
                dictData.getDictLabel(),
                dictData.getDictValue(),
                dictData.getCssClass(),
                dictData.getListClass(),
                dictData.getIsDefault(),
                dictData.getStatus(),
                dictData.getRemark(),
                dictData.getCreatedAt()
        );
    }
}
