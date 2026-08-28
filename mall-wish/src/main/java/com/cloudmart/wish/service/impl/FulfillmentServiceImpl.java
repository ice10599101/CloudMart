package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.SubmitFulfillmentRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishFulfillment;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.FulfillmentService;
import com.cloudmart.wish.service.LegacyFlowService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.WishFulfillmentSubmitVO;
import com.cloudmart.wish.vo.InheritResultVO;
import com.cloudmart.wish.vo.WishFulfillmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 还愿服务实现（文档 2.4 节，Sprint 1.10）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>统一即时生效（产品决策 2026-08-20）：提交即 FULFILLED + BLOOM，
 *       先发后审；uk_fulfillment_wish + 状态条件 UPDATE 双保险防重复提交</li>
 *   <li>作者级防存在性探测：不可见心愿对非作者统一 404（与 updateWish 同模式）</li>
 *   <li>统计/星光/徽章与还愿落库同一事务，回滚时全部撤销</li>
 *   <li>故事与感悟 XSS 转义后存储；audit_status=PENDING 供管理端待审筛选
 *       （表⑨无 sensitive_hit 列，敏感词标记以 PENDING 状态承载）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentServiceImpl implements FulfillmentService {

    /** 还愿星光奖励（文档 6.1：还愿完成 +50） */
    static final int FULFILL_STARLIGHT_REWARD = 50;

    private final WishMapper wishMapper;
    private final WishFulfillmentMapper wishFulfillmentMapper;
    private final UserStatService userStatService;
    private final UserFeignClient userFeignClient;
    private final WishContentSanitizer contentSanitizer;
    private final LegacyFlowService legacyFlowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WishFulfillmentSubmitVO submitFulfillment(Long userId, Long wishId, SubmitFulfillmentRequest request) {
        // 作者级前置校验：不可见心愿统一 404 防存在性探测，可见但非作者 403
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !isViewableByUser(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (!wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅作者可还愿此心愿");
        }

        // 状态前置校验：仅 ACTIVE/OVERDUE 可发起还愿（文档 2.4 errors）
        if (wish.getStatus() != WishStatus.ACTIVE && wish.getStatus() != WishStatus.OVERDUE) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FULFILLABLE,
                    "仅进行中或已过期的心愿可还愿，当前状态: " + wish.getStatus());
        }

        // 内容净化：路径穿越拦截 + XSS 转义（先发后审，audit_status=PENDING 标记待审）
        String story = request.story().trim();
        if (!contentSanitizer.isFreeOfPathTraversal(story)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "还愿故事包含非法字符");
        }
        String feeling = request.feeling() == null ? null : contentSanitizer.escapeHtml(request.feeling().trim());

        WishFulfillment fulfillment = new WishFulfillment();
        fulfillment.setWishId(wishId);
        fulfillment.setUserId(userId);
        fulfillment.setStory(contentSanitizer.escapeHtml(story));
        fulfillment.setMediaUrls(WishJsonUtils.stringifyList(request.mediaUrls()));
        fulfillment.setFeeling(feeling);
        fulfillment.setAuditStatus(AuditStatus.PENDING);
        fulfillment.setIsVisible(true);
        fulfillment.setIsInherited(false);
        wishFulfillmentMapper.insert(fulfillment);

        // 心愿状态条件流转（并发双保险）：查询与更新间状态可能变化
        int affected = wishMapper.update(null,
                new LambdaUpdateWrapper<Wish>()
                        .eq(Wish::getId, wishId)
                        .in(Wish::getStatus, WishStatus.ACTIVE, WishStatus.OVERDUE)
                        .set(Wish::getStatus, WishStatus.FULFILLED)
                        .set(Wish::getFruitType, FruitType.BLOOM)
                        .set(Wish::getFulfilledAt, LocalDateTime.now()));
        if (affected == 0) {
            // 并发重复提交（或状态已被并发流转）：回滚整个事务
            throw new BusinessException(WishErrorCodes.WISH_NOT_FULFILLABLE, "心愿状态已变更，无法还愿");
        }

        // 同事务联动：统计（total_fulfilled +1 / active_wishes -1）+ 徽章判定
        List<WishBadge> awardedBadges = userStatService.incrementOnFulfilled(userId);

        // 同事务发放星光（文档 6.1 还愿完成 +50，上限截断记实际入账）
        int credited = userStatService.earnStarlight(
                userId, FULFILL_STARLIGHT_REWARD, ResourceLogSource.FULFILL, fulfillment.getId());

        log.info("还愿提交成功, wishId={}, userId={}, fulfillmentId={}, starlightCredited={}",
                wishId, userId, fulfillment.getId(), credited);

        // 事务提交后异步内容流转（community 帖子生成；失败重试/日志，不阻断还愿）
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            legacyFlowService.submitContentFlow(wishId, fulfillment.getId());
                        }
                    });
        }

        return new WishFulfillmentSubmitVO(
                fulfillment.getId(),
                wishId,
                WishStatus.FULFILLED,
                FruitType.BLOOM,
                awardedBadges.stream()
                        .map(b -> new WishFulfillmentSubmitVO.BadgeAwardedVO(b.getId(), b.getName()))
                        .toList(),
                credited,
                fulfillment.getCreatedAt()
        );
    }

    @Override
    public WishFulfillmentVO getFulfillmentDetail(Long wishId, Long viewerId) {
        // 可见性校验与心愿详情同语义：PRIVATE/TREE_HOLE 非作者、审核隐藏 → 404
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !isViewableByUser(wish, viewerId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        // 未还愿或已撤回还愿故事（软删）→ 404
        WishFulfillment fulfillment = wishFulfillmentMapper.selectOne(
                new LambdaQueryWrapper<WishFulfillment>().eq(WishFulfillment::getWishId, wishId));
        if (fulfillment == null || Boolean.FALSE.equals(fulfillment.getIsVisible())) {
            throw new BusinessException(WishErrorCodes.WISH_FULFILLMENT_NOT_FOUND, "还愿记录不存在");
        }

        AuthorInfo author = fetchAuthorInfo(wish.getUserId());
        return new WishFulfillmentVO(
                fulfillment.getId(),
                wishId,
                fulfillment.getStory(),
                WishJsonUtils.parseStringList(fulfillment.getMediaUrls()),
                fulfillment.getFeeling(),
                wish.getUserId(),
                author.nickname(),
                author.avatar(),
                fulfillment.getCreatedAt()
        );
    }

    // ---------------- 私有方法 ----------------

    /**
     * 可见性判定（与 WishServiceImpl#isViewableByUser 同语义）：
     * 作者始终可见；PRIVATE/TREE_HOLE 非作者不可见；
     * 审核驳回/隐藏、is_visible=false 不可见。
     */
    private boolean isViewableByUser(Wish wish, Long userId) {
        if (wish.getDeletedAt() != null) {
            return false;
        }
        if (wish.getUserId().equals(userId)) {
            return true;
        }
        if (wish.getVisibility() != WishVisibility.PUBLIC) {
            return false;
        }
        if (wish.getAuditStatus() != AuditStatus.APPROVED && wish.getAuditStatus() != AuditStatus.PENDING) {
            return false;
        }
        return !Boolean.FALSE.equals(wish.getIsVisible());
    }

    /** 作者信息获取（Feign 失败降级为占位值，不阻塞还愿详情）。 */
    private AuthorInfo fetchAuthorInfo(Long userId) {
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(Set.of(userId)));
            if (response.success() && response.data() != null && !response.data().isEmpty()) {
                Map<String, Object> m = response.data().get(0);
                return new AuthorInfo(
                        ((Number) m.get("id")).longValue(),
                        (String) m.getOrDefault("nickname", "心愿旅人"),
                        (String) m.getOrDefault("avatar", "")
                );
            }
        } catch (Exception e) {
            log.warn("获取作者信息失败，降级为占位数据: {}", e.getMessage());
        }
        return AuthorInfo.placeholder(userId);
    }

    /** 内部作者信息载体。 */
    private record AuthorInfo(Long userId, String nickname, String avatar) {
        static AuthorInfo placeholder(Long userId) {
            return new AuthorInfo(userId, "心愿旅人", "");
        }
    }


    // ---------------- Sprint 2.7：传承 + 撤回 ----------------

    @Override
    public InheritResultVO inheritFulfillment(Long userId, Long wishId, String message) {
        return legacyFlowService.pushInherit(userId, wishId, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawFulfillment(Long userId, Long wishId) {
        // 作者级防存在性探测：不可见统一 404，可见但非作者 403（与提交同口径）
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !isViewableByUser(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        if (!wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅作者可撤回还愿故事");
        }
        WishFulfillment fulfillment = wishFulfillmentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WishFulfillment>()
                        .eq(WishFulfillment::getWishId, wishId)
                        .isNull(WishFulfillment::getDeletedAt)
                        .last("LIMIT 1"));
        if (fulfillment == null) {
            throw new BusinessException(WishErrorCodes.WISH_FULFILLMENT_NOT_FOUND, "还愿记录不存在");
        }
        // 软删保留审计；心愿状态保持 FULFILLED（历史事实不回退）
        fulfillment.setDeletedAt(java.time.LocalDateTime.now());
        wishFulfillmentMapper.updateById(fulfillment);

        // 状态同步：community 帖子隐藏（文档 2.7 还愿删除 → 帖子同步隐藏）
        legacyFlowService.hideFlow(fulfillment.getId());
        log.info("还愿故事已撤回, wishId={}, fulfillmentId={}", wishId, fulfillment.getId());
    }
}
