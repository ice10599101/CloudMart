package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.entity.WishCheckin;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishCheckinMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AdminWishService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.AdminWishStatsVO;
import com.cloudmart.wish.vo.AdminWishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final WishCheckinMapper wishCheckinMapper;
    private final WishInteractionMapper wishInteractionMapper;
    private final com.cloudmart.wish.service.UserStatService userStatService;
    private final WishOutboxService outboxService;

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
    public AdminWishVO auditWish(Long wishId, AdminAuditWishRequest request, Long actorId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        AuditStatus newStatus = request.auditStatus();
        AuditStatus oldStatus = wish.getAuditStatus();
        if (oldStatus == newStatus) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "心愿已是该审核状态: " + newStatus);
        }

        boolean visible;
        switch (newStatus) {
            case APPROVED -> visible = true;
            case REJECTED -> {
                // B11：驳回原因必填（422），并随治理决定落库
                if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                    throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "驳回原因不能为空");
                }
                visible = false;
            }
            case AUTO_HIDDEN -> visible = false;
            default -> throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "不支持的审核状态: " + newStatus);
        }

        // B11：CAS 条件更新——两名管理员并发审核只有一个成功；不覆盖其他字段
        int affected = wishMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Wish>()
                        .eq(Wish::getId, wishId)
                        .eq(Wish::getAuditStatus, oldStatus)
                        .set(Wish::getAuditStatus, newStatus)
                        .set(Wish::getIsVisible, visible)
                        .set(Wish::getRejectReason,
                                newStatus == AuditStatus.REJECTED ? request.rejectReason().trim() : null));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "审核状态已被其他管理员变更，请刷新后重试");
        }

        // B13：治理事件与领域写同事务（通知作者走消费端）
        outboxService.publish("WISH", wishId, 0L, "WishModerated",
                java.util.Map.of("wishId", wishId, "auditStatus", newStatus.name(),
                        "actorId", actorId == null ? 0L : actorId, "rejected",
                        newStatus == AuditStatus.REJECTED));

        log.info("心愿审核完成, wishId={}, actorId={}, status={}→{}, visible={}",
                wishId, actorId, oldStatus, newStatus, visible);

        Wish updated = wishMapper.selectById(wishId);
        Map<Long, String> categoryNameMap = fetchCategoryNames(Set.of(updated.getCategoryId()));
        return toAdminWishVO(updated, categoryNameMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminWishVO updateVisibility(Long wishId, Boolean visible, Long actorId) {
        Wish wish = requireWish(wishId);
        // B11：被驳回/自动隐藏内容不能只翻 isVisible 恢复——恢复必须走审核接口改审核状态
        if (Boolean.TRUE.equals(visible)
                && (wish.getAuditStatus() == AuditStatus.REJECTED
                || wish.getAuditStatus() == AuditStatus.AUTO_HIDDEN)) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT,
                    "内容处于驳回/自动隐藏状态，恢复请通过审核接口");
        }
        // CAS：并发上下架只有一个成功
        int affected = wishMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Wish>()
                        .eq(Wish::getId, wishId)
                        .eq(Wish::getIsVisible, !Boolean.TRUE.equals(visible))
                        .set(Wish::getIsVisible, Boolean.TRUE.equals(visible)));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_STATUS_CONFLICT, "展示状态已被并发变更");
        }
        outboxService.publish("WISH", wishId, 0L, "WishVisibilityChanged",
                java.util.Map.of("wishId", wishId, "visible", Boolean.TRUE.equals(visible),
                        "actorId", actorId == null ? 0L : actorId));
        log.info("心愿上下架完成, wishId={}, actorId={}, visible={}", wishId, actorId, visible);
        return requeryWishVO(wish.getCategoryId(), wishId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminWishVO updateTop(Long wishId, Boolean isTop) {
        Wish wish = requireWish(wishId);
        Wish updateEntity = new Wish();
        updateEntity.setId(wishId);
        updateEntity.setIsTop(isTop);
        wishMapper.updateById(updateEntity);

        log.info("心愿置顶变更完成, wishId={}, isTop={}", wishId, isTop);
        return requeryWishVO(wish.getCategoryId(), wishId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWish(Long wishId, Long actorId) {
        Wish wish = requireWish(wishId);
        // B10 一致口径：仅活跃集合删除扣 activeWishes，避免误减其他心愿
        boolean isActiveSet = wish.getStatus() == com.cloudmart.wish.enums.WishStatus.ACTIVE
                || wish.getStatus() == com.cloudmart.wish.enums.WishStatus.OVERDUE
                || wish.getStatus() == com.cloudmart.wish.enums.WishStatus.FULFILLING;
        int affected = wishMapper.delete(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getId, wishId)
                .eq(isActiveSet, Wish::getStatus, wish.getStatus()));
        if (affected == 0 && isActiveSet) {
            affected = wishMapper.delete(new LambdaQueryWrapper<Wish>().eq(Wish::getId, wishId));
        }
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在或已删除");
        }
        if (isActiveSet && affected == 1) {
            userStatService.decrementOnWishDeleted(wish.getUserId());
        }
        outboxService.publish("WISH", wishId, 0L, "WishDeleted",
                java.util.Map.of("wishId", wishId, "userId", wish.getUserId(),
                        "actorId", actorId == null ? 0L : actorId, "byAdmin", true));
        log.info("心愿已软删, wishId={}, actorId={}, activeSet={}", wishId, actorId, isActiveSet);
    }

    private Wish requireWish(Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        return wish;
    }

    private AdminWishVO requeryWishVO(Long categoryId, Long wishId) {
        Wish updated = wishMapper.selectById(wishId);
        Map<Long, String> categoryNameMap = fetchCategoryNames(Set.of(categoryId));
        return toAdminWishVO(updated, categoryNameMap);
    }

    @Override
    public AdminWishStatsVO stats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        Long totalWishCount = wishMapper.selectCount(null);
        Long todayWishCount = wishMapper.selectCount(
                new LambdaQueryWrapper<Wish>().ge(Wish::getCreatedAt, todayStart));
        Long activeWishCount = wishMapper.selectCount(
                new LambdaQueryWrapper<Wish>().eq(Wish::getStatus, WishStatus.ACTIVE));
        Long fulfilledWishCount = wishMapper.selectCount(
                new LambdaQueryWrapper<Wish>().eq(Wish::getStatus, WishStatus.FULFILLED));
        Long todayCheckinCount = wishCheckinMapper.selectCount(
                new LambdaQueryWrapper<WishCheckin>().eq(WishCheckin::getCheckinDate, today));
        // 互动统计排除已取消（软删）记录，与用户端互动热度口径一致
        Long todayInteractionCount = wishInteractionMapper.selectCount(
                new LambdaQueryWrapper<WishInteraction>()
                        .isNull(WishInteraction::getDeletedAt)
                        .ge(WishInteraction::getCreatedAt, todayStart));

        return new AdminWishStatsVO(totalWishCount, todayWishCount, activeWishCount,
                fulfilledWishCount, todayCheckinCount, todayInteractionCount);
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
                Boolean.TRUE.equals(wish.getIsTop()),
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
