package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateCategoryRequest;
import com.cloudmart.wish.dto.UpdateCategoryRequest;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.service.CategoryService;
import com.cloudmart.wish.vo.CategoryVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 心愿分类服务实现。
 *
 * <p>字典类数据采用 Cache-Aside 策略：写操作删除缓存（不更新缓存），
 * TTL 1h + 随机抖动 0-5min（避免缓存雪崩）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private static final String CACHE_KEY = "wish:categories";
    private static final long CACHE_TTL_SECONDS = 3600; // 1h
    private static final long CACHE_JITTER_MAX_SECONDS = 300; // 0-5min 抖动

    private final WishCategoryMapper wishCategoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<CategoryVO> listCategories() {
        // Cache-Aside: 先查缓存
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached instanceof List<?> list && !list.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<CategoryVO> result = (List<CategoryVO>) list;
            return result;
        }

        // 缓存未命中，回源 DB
        List<WishCategory> categories = wishCategoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WishCategory>()
                        .orderByAsc(WishCategory::getSort)
        );

        List<CategoryVO> result = categories.stream()
                .map(c -> new CategoryVO(c.getId(), c.getCode(), c.getName(), c.getIcon(), c.getSort()))
                .toList();

        // 回填缓存（TTL + 抖动）
        long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(CACHE_JITTER_MAX_SECONDS);
        redisTemplate.opsForValue().set(CACHE_KEY, result, ttl, TimeUnit.SECONDS);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO createCategory(CreateCategoryRequest request) {
        // 检查 code 唯一性
        Long existingCount = wishCategoryMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WishCategory>()
                        .eq(WishCategory::getCode, request.code())
        );
        if (existingCount > 0) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "分类编码已存在: " + request.code());
        }

        WishCategory category = new WishCategory();
        category.setCode(request.code());
        category.setName(request.name());
        category.setSort(request.sort() != null ? request.sort() : 0);
        category.setIcon(request.icon());
        wishCategoryMapper.insert(category);

        evictCache();

        return new CategoryVO(category.getId(), category.getCode(), category.getName(),
                category.getIcon(), category.getSort());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO updateCategory(Long id, UpdateCategoryRequest request) {
        WishCategory category = wishCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(WishErrorCodes.WISH_CATEGORY_NOT_FOUND, "分类不存在");
        }

        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.sort() != null) {
            category.setSort(request.sort());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
        wishCategoryMapper.updateById(category);

        evictCache();

        return new CategoryVO(category.getId(), category.getCode(), category.getName(),
                category.getIcon(), category.getSort());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        WishCategory category = wishCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(WishErrorCodes.WISH_CATEGORY_NOT_FOUND, "分类不存在");
        }

        // 系统预设分类（id < 2000）禁止删除
        if (id < 2000) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "系统预设分类不可删除");
        }

        wishCategoryMapper.deleteById(id);
        evictCache();
    }

    private void evictCache() {
        redisTemplate.delete(CACHE_KEY);
        log.debug("分类缓存已清除");
    }
}
