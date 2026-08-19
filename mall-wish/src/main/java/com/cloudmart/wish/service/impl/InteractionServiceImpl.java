package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.InteractionListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.InteractionType;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.WishStatEventProducer;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.InteractionService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.InteractionItemVO;
import com.cloudmart.wish.vo.InteractionResultVO;
import com.cloudmart.wish.vo.InteractionRevokeVO;
import com.cloudmart.wish.vo.MyInteractionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 心愿互动服务实现（Sprint 1.2 核心，文档 2.2/4.1/6.1 节）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>限频（Redis）在方法入口、DB 操作之前执行——事务内不持有 DB 锁时做网络调用</li>
 *   <li>同求唯一三道防线：Redis SETNX 占位（快速拒绝）→ DB 存在性校验 →
 *       {@code uk_interaction_unique} 函数唯一索引（最终正确性保障）</li>
 *   <li>星光扣减/发放与互动落库同事务（文档 6.4：流水与余额同事务）</li>
 *   <li>作者星光日上限按"含软删"的总互动数判定——取消互动不退已发星光，
 *       若按未删除计数会出现"取消→重新互动"重复发薪漏洞</li>
 *   <li>total_helped 经 MQ 事务提交后异步累加（文档 6.5），失败由对账兜底</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    /** 点亮单次消耗星光（文档 6.2） */
    static final int LIGHT_COST = 2;
    /** 作者被点亮单次获得（文档 6.1） */
    static final int EARN_PER_LIGHT = 1;
    /** 作者被同求单次获得（文档 6.1） */
    static final int EARN_PER_SAME_WISH = 2;
    /** 作者被点亮每日获得上限（文档 6.1） */
    static final int DAILY_EARN_LIGHT_CAP = 20;
    /** 作者被同求每日获得上限（文档 6.1） */
    static final int DAILY_EARN_SAME_WISH_CAP = 50;

    /** 星光上限判定与限频共用的平台运营时区 */
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private final WishMapper wishMapper;
    private final WishInteractionMapper wishInteractionMapper;
    private final UserStatService userStatService;
    private final InteractionRateLimiter rateLimiter;
    private final WishContentSanitizer contentSanitizer;
    private final WishStatEventProducer statEventProducer;
    private final UserFeignClient userFeignClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public InteractionResultVO createInteraction(Long userId, Long wishId, CreateInteractionRequest request) {
        InteractionType type = request.type();

        // ---- 前置校验（无 DB 写、无锁持有）----
        if (type == InteractionType.ANON_STAR) {
            throw new BusinessException(WishErrorCodes.WISH_INTERACTION_TYPE_INVALID,
                    "匿名星光将在后续版本开放");
        }

        Wish wish = requireInteractableWish(wishId, userId);
        ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(userId));

        // ---- 限频（Redis，Fail-Open；DB 唯一约束兜底）----
        if (!rateLimiter.checkUserDailyLimit(userId, type, userZone)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED,
                    "今日" + interactionLabel(type) + "次数已达上限");
        }
        if (type == InteractionType.LIGHT && !rateLimiter.checkWishLightLimit(wishId)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "该心愿今日点亮已达上限");
        }
        if (type == InteractionType.BLESS && !rateLimiter.checkBlessPerWish(userId, wishId, userZone)) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "今日已祝福过该心愿");
        }
        boolean sameWishAcquired = false;
        if (type == InteractionType.SAME_WISH) {
            sameWishAcquired = rateLimiter.tryAcquireSameWishUnique(userId, wishId);
            if (!sameWishAcquired) {
                throw new BusinessException(WishErrorCodes.WISH_ALREADY_INTERACTED, "已同求过该心愿");
            }
        }

        // ---- 业务校验 ----
        final String blessContent = type == InteractionType.BLESS
                ? requireBlessContent(request.content()) : null;

        try {
            // 编程式事务：限频等前置检查在事务外执行，避免事务内做 Redis 网络调用
            return transactionTemplate.execute(status ->
                    doCreateInteraction(userId, wish, type, blessContent));
        } catch (BusinessException ex) {
            // 同求占位成功但落库失败（唯一约束冲突等）：释放占位，允许客户端重试
            if (sameWishAcquired && ex.getCode() != null
                    && (WishErrorCodes.WISH_ALREADY_INTERACTED.equals(ex.getCode()))) {
                rateLimiter.releaseSameWishUnique(userId, wishId);
            }
            throw ex;
        }
    }

    /**
     * 互动落库事务体：由 {@link TransactionTemplate} 驱动（createInteraction 经自调用，
     * 注解事务不生效，故采用编程式事务）。
     */
    private InteractionResultVO doCreateInteraction(Long userId, Wish wish,
                                                    InteractionType type, String blessContent) {
        Long wishId = wish.getId();

        // 同求 DB 存在性校验（Redis Fail-Open 时的兜底；最终兜底为唯一索引）
        if (type == InteractionType.SAME_WISH) {
            Long existing = wishInteractionMapper.selectCount(
                    new LambdaQueryWrapper<WishInteraction>()
                            .eq(WishInteraction::getWishId, wishId)
                            .eq(WishInteraction::getUserId, userId)
                            .eq(WishInteraction::getType, type));
            if (existing != null && existing > 0) {
                throw new BusinessException(WishErrorCodes.WISH_ALREADY_INTERACTED, "已同求过该心愿");
            }
        }

        // ---- 互动落库 ----
        WishInteraction interaction = new WishInteraction();
        interaction.setWishId(wishId);
        interaction.setUserId(userId);
        interaction.setType(type);
        interaction.setContent(blessContent);
        interaction.setStarlightCost(type == InteractionType.LIGHT ? LIGHT_COST : 0);
        wishInteractionMapper.insert(interaction);

        // ---- 星光结算（同事务，文档 6.4）----
        if (type == InteractionType.LIGHT) {
            userStatService.spendStarlight(userId, LIGHT_COST, ResourceLogSource.LIGHT_OTHER, interaction.getId());
            earnForAuthor(wish, InteractionType.LIGHT, EARN_PER_LIGHT,
                    DAILY_EARN_LIGHT_CAP, ResourceLogSource.LIGHTED, interaction.getId());
            publishHelpedAfterCommit(userId);
        } else if (type == InteractionType.SAME_WISH) {
            earnForAuthor(wish, InteractionType.SAME_WISH, EARN_PER_SAME_WISH,
                    DAILY_EARN_SAME_WISH_CAP, ResourceLogSource.SAME_WISHED, interaction.getId());
        }
        // BLESS 无星光变化

        // ---- 心愿计数原子更新（支持度生成列自动联动）----
        updateWishCounter(wishId, type, +1);

        Wish latest = wishMapper.selectById(wishId);
        log.info("互动成功, wishId={}, userId={}, type={}, interactionId={}",
                wishId, userId, type, interaction.getId());
        return new InteractionResultVO(
                interaction.getId(),
                type,
                latest != null ? latest.getLightCount() : null,
                latest != null ? latest.getSameWishCount() : null,
                latest != null ? latest.getBlessCount() : null,
                interaction.getStarlightCost()
        );
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public InteractionRevokeVO revokeInteraction(Long userId, Long wishId, Long interactionId) {
        requireInteractableWish(wishId, userId);

        WishInteraction interaction = wishInteractionMapper.selectById(interactionId);
        if (interaction == null || !wishId.equals(interaction.getWishId())) {
            throw new BusinessException(WishErrorCodes.WISH_INTERACTION_NOT_FOUND, "互动记录不存在");
        }
        if (!userId.equals(interaction.getUserId())) {
            throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "只能取消自己的互动");
        }

        // 软删（@TableLogic）：保留审计轨迹；已扣/已发星光不退还（文档 6.1 取消规则）
        wishInteractionMapper.deleteById(interactionId);

        updateWishCounter(wishId, interaction.getType(), -1);
        if (interaction.getType() == InteractionType.SAME_WISH) {
            // 释放同求唯一占位，允许重新同求（与函数唯一索引"未删除时唯一"语义一致）
            rateLimiter.releaseSameWishUnique(userId, wishId);
        }

        log.info("互动取消, wishId={}, userId={}, type={}, interactionId={}",
                wishId, userId, interaction.getType(), interactionId);
        return new InteractionRevokeVO(interactionId, interaction.getType(), true);
    }

    @Override
    public InteractionPage listInteractions(Long wishId, Long viewerId, InteractionListQuery query) {
        requireInteractableWish(wishId, viewerId);
        int pageSize = query.safePageSize();
        Long cursor = parseCursor(query.cursor());

        LambdaQueryWrapper<WishInteraction> wrapper = new LambdaQueryWrapper<WishInteraction>()
                .eq(WishInteraction::getWishId, wishId)
                .orderByDesc(WishInteraction::getId)
                .last("LIMIT " + (pageSize + 1));
        if (query.type() != null) {
            wrapper.eq(WishInteraction::getType, query.type());
        }
        if (cursor != null) {
            wrapper.lt(WishInteraction::getId, cursor);
        }

        List<WishInteraction> interactions = wishInteractionMapper.selectList(wrapper);
        boolean hasMore = interactions.size() > pageSize;
        List<WishInteraction> pageItems = hasMore ? interactions.subList(0, pageSize) : interactions;

        List<InteractionItemVO> records = toItemsWithUserInfo(pageItems);
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? String.valueOf(pageItems.get(pageItems.size() - 1).getId()) : null;
        return new InteractionPage(records, nextCursor, hasMore);
    }

    @Override
    public List<MyInteractionVO> listMyInteractions(Long userId, Long wishId) {
        requireInteractableWish(wishId, userId);
        // DB 时间按平台时区存储；"今日"按用户时区判定（与限频口径一致）
        ZoneId userZone = ZoneId.of(userStatService.getUserTimezone(userId));
        LocalDate today = LocalDate.now(userZone);
        List<WishInteraction> interactions = wishInteractionMapper.selectList(
                new LambdaQueryWrapper<WishInteraction>()
                        .eq(WishInteraction::getWishId, wishId)
                        .eq(WishInteraction::getUserId, userId)
                        .orderByDesc(WishInteraction::getId));
        return interactions.stream()
                .map(i -> new MyInteractionVO(
                        i.getId(), i.getType(), i.getContent(),
                        i.getCreatedAt(),
                        i.getCreatedAt() != null
                                && today.equals(i.getCreatedAt()
                                        .atZone(PLATFORM_ZONE)
                                        .withZoneSameInstant(userZone)
                                        .toLocalDate())))
                .toList();
    }

    // ---------------- 私有方法 ----------------

    /**
     * 心愿可互动性校验：不存在/软删/PRIVATE/TREE_HOLE 非作者/审核隐藏 → 404（不暴露存在性）。
     * 可见性语义与 WishServiceImpl#getWishDetail 保持一致。
     */
    private Wish requireInteractableWish(Long wishId, Long userId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !isViewableByUser(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        return wish;
    }

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

    /**
     * 祝福内容校验与净化：非空、≤200、路径穿越拦截、XSS 转义。
     */
    private String requireBlessContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "祝福内容不能为空");
        }
        if (!contentSanitizer.isFreeOfPathTraversal(raw)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "祝福内容包含非法字符");
        }
        return contentSanitizer.escapeHtml(raw.trim());
    }

    /**
     * 作者星光发放（含每日上限判定）。
     * 计数含软删记录：取消互动不退星光，按历史事实计数防止"取消→重发"刷薪漏洞。
     */
    private void earnForAuthor(Wish wish, InteractionType type, int amount, int dailyCap,
                               ResourceLogSource source, Long interactionId) {
        LocalDateTime todayStart = LocalDate.now(PLATFORM_ZONE).atStartOfDay();
        long todayCount = wishInteractionMapper.countIncludingDeletedSince(
                wish.getId(), type.name(), todayStart);
        if (todayCount > dailyCap) {
            log.debug("作者星光达每日上限不再发放, wishId={}, type={}, todayCount={}, cap={}",
                    wish.getId(), type, todayCount, dailyCap);
            return;
        }
        userStatService.earnStarlight(wish.getUserId(), amount, source, interactionId);
    }

    /**
     * 心愿互动计数原子更新（delta=+1/-1，GREATEST 防负数）。
     */
    private void updateWishCounter(Long wishId, InteractionType type, int delta) {
        String column = switch (type) {
            case LIGHT -> "light_count";
            case SAME_WISH -> "same_wish_count";
            case BLESS -> "bless_count";
            case ANON_STAR -> throw new IllegalArgumentException("ANON_STAR 未启用");
        };
        String sql = column + " = GREATEST(" + column + " + (" + delta + "), 0)";
        wishMapper.update(null, new LambdaUpdateWrapper<Wish>()
                .eq(Wish::getId, wishId)
                .setSql(sql));
    }

    /**
     * 事务提交后发送帮助统计事件（避免回滚后统计多加；发送失败对账兜底）。
     */
    private void publishHelpedAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    statEventProducer.publishHelpedEvent(userId);
                }
            });
        } else {
            statEventProducer.publishHelpedEvent(userId);
        }
    }

    private List<InteractionItemVO> toItemsWithUserInfo(List<WishInteraction> interactions) {
        if (interactions.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = interactions.stream()
                .map(WishInteraction::getUserId).collect(Collectors.toSet());
        Map<Long, UserInfo> userMap = fetchUserInfo(userIds);
        return interactions.stream()
                .map(i -> {
                    UserInfo info = userMap.getOrDefault(i.getUserId(), UserInfo.placeholder(i.getUserId()));
                    return new InteractionItemVO(
                            i.getId(), i.getUserId(), info.nickname(), info.avatar(),
                            i.getType(), i.getContent(), i.getCreatedAt());
                })
                .toList();
    }

    private Map<Long, UserInfo> fetchUserInfo(Set<Long> userIds) {
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> new UserInfo(
                                        ((Number) m.get("id")).longValue(),
                                        (String) m.getOrDefault("nickname", "心愿旅人"),
                                        (String) m.getOrDefault("avatar", ""))
                        ));
            }
        } catch (Exception e) {
            log.warn("批量获取互动用户信息失败，降级为占位数据: {}", e.getMessage());
        }
        return userIds.stream().collect(Collectors.toMap(
                id -> id, id -> UserInfo.placeholder(id)));
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }

    private String interactionLabel(InteractionType type) {
        return switch (type) {
            case LIGHT -> "点亮";
            case SAME_WISH -> "同求";
            case BLESS -> "祝福";
            case ANON_STAR -> "匿名星光";
        };
    }

    /**
     * 用户展示信息（Feign 结果的内部载体）。
     */
    private record UserInfo(Long userId, String nickname, String avatar) {
        static UserInfo placeholder(Long userId) {
            return new UserInfo(userId, "心愿旅人", "");
        }
    }
}
