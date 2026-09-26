package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishAiGoal;
import com.cloudmart.wish.enums.GoalStatus;
import com.cloudmart.wish.repository.WishAiGoalMapper;
import com.cloudmart.wish.repository.WishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 心愿目标计划服务（N04）：每心愿最多 20 步；用户可直接建步骤（AI 仅草案）；
 * 编辑/勾选完成走 version CAS；批量排序校验集合完整且同心愿。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalPlanService {

    private static final int MAX_GOALS_PER_WISH = 20;

    private final WishAiGoalMapper goalMapper;
    private final WishMapper wishMapper;

    private Wish requireOwnedWish(Long userId, Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null || !wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        return wish;
    }

    public List<WishAiGoal> listGoals(Long userId, Long wishId) {
        requireOwnedWish(userId, wishId);
        return goalMapper.selectList(new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getWishId, wishId)
                .isNull(WishAiGoal::getDeletedAt)
                .orderByAsc(WishAiGoal::getSortOrder)
                .orderByAsc(WishAiGoal::getId));
    }

    @Transactional
    public WishAiGoal createGoal(Long userId, Long wishId, String title, String description,
                                 Integer estimatedDays, Integer priority, Integer sortOrder) {
        requireOwnedWish(userId, wishId);
        if (title == null || title.isBlank() || title.length() > 120) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "目标标题须为1-120字");
        }
        Long count = goalMapper.selectCount(new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getWishId, wishId)
                .isNull(WishAiGoal::getDeletedAt));
        if (count != null && count >= MAX_GOALS_PER_WISH) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "每个心愿最多 20 个步骤");
        }
        WishAiGoal goal = new WishAiGoal();
        goal.setUserId(userId);
        goal.setWishId(wishId);
        goal.setTitle(title.trim());
        goal.setDescription(description);
        goal.setEstimatedDays(estimatedDays == null ? 7 : estimatedDays);
        goal.setPriority(priority == null ? 3 : priority);
        goal.setSortOrder(sortOrder == null ? (count == null ? 0 : count.intValue()) : sortOrder);
        goal.setStatus(GoalStatus.PENDING);
        goal.setVersion(0);
        goalMapper.insert(goal);
        return goal;
    }

    @Transactional
    public WishAiGoal updateGoal(Long userId, Long goalId, String title, String description,
                                 GoalStatus status, Long version) {
        WishAiGoal goal = requireOwnedGoal(userId, goalId);
        requireOwnedWish(userId, goal.getWishId());
        if (version == null || goal.getVersion() == null || !version.equals(goal.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        int affected = goalMapper.update(null, new LambdaUpdateWrapper<WishAiGoal>()
                .eq(WishAiGoal::getId, goalId)
                .eq(WishAiGoal::getVersion, version)
                .setSql("version = version + 1")
                .set(title != null, WishAiGoal::getTitle, title)
                .set(description != null, WishAiGoal::getDescription, description)
                .set(status != null, WishAiGoal::getStatus, status)
                .set(status == GoalStatus.COMPLETED,
                        WishAiGoal::getCompletedAt, LocalDateTime.now(java.time.ZoneId.of("UTC"))));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        return goalMapper.selectById(goalId);
    }

    @Transactional
    public void deleteGoal(Long userId, Long goalId, Long version) {
        WishAiGoal goal = requireOwnedGoal(userId, goalId);
        if (version == null || goal.getVersion() == null || !version.equals(goal.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        // 软删（@TableLogic）
        goalMapper.deleteById(goalId);
    }

    /** 批量排序：集合必须完整且属于同一心愿（N04 验收）。 */
    @Transactional
    public void reorder(Long userId, Long wishId, Map<Long, Integer> goalOrder) {
        requireOwnedWish(userId, wishId);
        List<WishAiGoal> existing = goalMapper.selectList(new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getWishId, wishId)
                .isNull(WishAiGoal::getDeletedAt));
        Set<Long> existingIds = existing.stream().map(WishAiGoal::getId).collect(Collectors.toSet());
        if (goalOrder == null || goalOrder.isEmpty() || !existingIds.equals(goalOrder.keySet())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "排序集合必须与当前目标集合完全一致");
        }
        Map<Long, WishAiGoal> byId = existing.stream()
                .collect(Collectors.toMap(WishAiGoal::getId, Function.identity()));
        goalOrder.forEach((goalId, order) -> {
            WishAiGoal goal = byId.get(goalId);
            if (goal.getVersion() != null && goal.getVersion() > 0) {
                goalMapper.update(null, new LambdaUpdateWrapper<WishAiGoal>()
                        .eq(WishAiGoal::getId, goalId)
                        .eq(WishAiGoal::getVersion, goal.getVersion())
                        .set(WishAiGoal::getSortOrder, order)
                        .setSql("version = version + 1"));
            }
        });
    }

    private WishAiGoal requireOwnedGoal(Long userId, Long goalId) {
        WishAiGoal goal = goalMapper.selectById(goalId);
        if (goal == null || goal.getDeletedAt() != null || !goal.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "目标不存在");
        }
        return goal;
    }
}
