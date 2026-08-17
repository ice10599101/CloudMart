package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AdminWishService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.AdminWishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台心愿服务实现。
 *
 * <p>提供心愿列表查看（offset 分页）和审核操作。
 * 管理后台不经过软删过滤，可查看已删除的心愿。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminWishServiceImpl implements AdminWishService {

    private final WishMapper wishMapper;
    private final WishCategoryMapper wishCategoryMapper;

    @Override
    public Page<AdminWishVO> listWishes(AdminWishListQuery query) {
        // 管理后台需要查看已软删的心愿，需要绕过 @TableLogic
        // 这里简化处理：Sprint 1.1 使用标准查询（软删的心愿不展示）
        // TODO Sprint 1.2: 如需查看已删除心愿，使用 @InterceptorIgnore(tenantLine = "true") 或自定义 SQL
        LambdaQueryWrapper<Wish> wrapper = new LambdaQueryWrapper<>();

        if (query.userId() != null) {
            wrapper.eq(Wish::getUserId, query.userId());
        }
        if (query.categoryId() != null) {
            wrapper.eq(Wish::getCategoryId, query.categoryId());
        }
        if (query.status() != null) {
            wrapper.eq(Wish::getStatus, query.status());
        }
        if (query.auditStatus() != null) {
            wrapper.eq(Wish::getAuditStatus, query.auditStatus());
        }
        if (query.visibility() != null) {
            wrapper.eq(Wish::getVisibility, query.visibility());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            wrapper.and(w -> w.like(Wish::getTitle, query.keyword())
                    .or().like(Wish::getDescription, query.keyword()));
        }

        wrapper.orderByDesc(Wish::getCreatedAt);

        Page<Wish> page = new Page<>(query.page(), query.pageSize());
        Page<Wish> wishPage = wishMapper.selectPage(page, wrapper);

        // 批量填充分类名称
        Set<Long> categoryIds = wishPage.getRecords().stream()
                .map(Wish::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = fetchCategoryNames(categoryIds);

        List<AdminWishVO> voList = wishPage.getRecords().stream()
                .map(w -> toAdminWishVO(w, categoryNameMap))
                .toList();

        Page<AdminWishVO> resultPage = new Page<>(wishPage.getCurrent(), wishPage.getSize(), wishPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public AdminWishVO getWishDetail(Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        Map<Long, String> categoryNameMap = fetchCategoryNames(Set.of(wish.getCategoryId()));
        return toAdminWishVO(wish, categoryNameMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminWishVO auditWish(Long wishId, AdminAuditWishRequest request) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        // 已审核状态再次审核返回冲突
        if (wish.getAuditStatus() != AuditStatus.PENDING) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "心愿已审核，不可重复审核");
        }

        AuditStatus newStatus = request.auditStatus();
        boolean visible;
        switch (newStatus) {
            case APPROVED -> visible = true;
            case REJECTED -> visible = false;
            case AUTO_HIDDEN -> visible = false;
            default -> throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "不支持的审核状态: " + newStatus);
        }

        // 使用 updateById 避免 LambdaUpdateWrapper 在单元测试中的 lambda cache 问题
        Wish updateEntity = new Wish();
        updateEntity.setId(wishId);
        updateEntity.setAuditStatus(newStatus);
        updateEntity.setIsVisible(visible);
        wishMapper.updateById(updateEntity);

        // TODO Sprint 1.2: 发送 RocketMQ wish-audited 事件通知作者

        log.info("心愿意审核完成, wishId={}, status={}, visible={}", wishId, newStatus, visible);

        // 重新查询返回最新数据
        Wish updated = wishMapper.selectById(wishId);
        Map<Long, String> categoryNameMap = fetchCategoryNames(Set.of(updated.getCategoryId()));
        return toAdminWishVO(updated, categoryNameMap);
    }

    private Map<Long, String> fetchCategoryNames(Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wishCategoryMapper.selectBatchIds(categoryIds)
                .stream()
                .collect(Collectors.toMap(WishCategory::getId, WishCategory::getName));
    }

    private AdminWishVO toAdminWishVO(Wish wish, Map<Long, String> categoryNameMap) {
        return new AdminWishVO(
                wish.getId(),
                wish.getUserId(),
                wish.getTitle(),
                wish.getDescription(),
                WishJsonUtils.parseStringList(wish.getMediaUrls()),
                wish.getCategoryId(),
                categoryNameMap.getOrDefault(wish.getCategoryId(), ""),
                WishJsonUtils.parseStringList(wish.getTags()),
                wish.getVisibility(),
                wish.getStatus(),
                wish.getFruitType(),
                wish.getAuditStatus(),
                wish.getAuditStrategy(),
                wish.getIsVisible(),
                wish.getLightCount(),
                wish.getSameWishCount(),
                wish.getBlessCount(),
                wish.getSupportCount(),
                wish.getExpectedAt(),
                wish.getFulfilledAt(),
                wish.getCreatedAt(),
                wish.getUpdatedAt(),
                wish.getDeletedAt()
        );
    }
}
