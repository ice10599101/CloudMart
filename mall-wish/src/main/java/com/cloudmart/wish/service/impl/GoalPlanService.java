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
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 心愿目标计划服务（N04/T12）：每心愿最多 20 步；用户可直接建步骤（AI 仅草案）；
 * 编辑/勾选完成走 version CAS；批量排序校验集合完整且同心愿。
 *
 * <p>T12 修复：</p>
 * <ul>
 *   <li>版本类型端到端统一为非负 Integer——旧请求 DTO 用 Long、实体用 Integer，
 *       {@code Long.equals(Integer)} 永不相等导致编辑/删除必然 409；</li>
 *   <li>删除为版本条件软删（CAS）——旧实现"先查后删"存在检查与删除之间的竞态；</li>
 *   <li>排序含 version=0 的目标（旧实现 {@code version > 0} 跳过新建目标），
 *       整批锁父心愿行后校验+更新，任一冲突全部回滚；</li>
 *   <li>新增步骤在父心愿行锁内计数，20 步上限并发安全（旧 count-then-insert
 *       两个并发创建可越过上限）；</li>
 *   <li>首次完成事实不被重写：COMPLETED→COMPLETED 不再改写 completedAt，
 *       恢复时正确清理 completedAt。</li>
 * </ul>
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
        // T12 真实环境暴露：列宽 title VARCHAR(100)——服务端限长与列对齐
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "目标标题须为1-100字");
        }
        // T12：锁父心愿行后计数——并发创建不能越过 20 步上限
        Wish locked = wishMapper.selectByIdForUpdate(wishId);
        if (locked == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        Long count = goalMapper.selectCount(new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getWishId, wishId)
                .isNull(WishAiGoal::getDeletedAt));
        if (count != null && count >= MAX_GOALS_PER_WISH) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "每个心愿最多 20 个步骤");
        }
        // T12 真实环境暴露：description 列 NOT NULL——null 插入会 SQL 异常（INTERNAL_ERROR）
        String safeDescription = description == null || description.isBlank()
                ? "" : description.trim();
        WishAiGoal goal = new WishAiGoal();
        goal.setUserId(userId);
        goal.setWishId(wishId);
        goal.setTitle(title.trim());
        goal.setDescription(safeDescription);
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
                                 GoalStatus status, Integer version) {
        WishAiGoal goal = requireOwnedGoal(userId, goalId);
        requireOwnedWish(userId, goal.getWishId());
        // T12：Integer 与 Integer 比较（旧 Long.equals(Integer) 恒 false）
        if (version == null || goal.getVersion() == null || !version.equals(goal.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        if (title != null && (title.isBlank() || title.length() > 100)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "目标标题须为1-100字");
        }
        boolean completing = status == GoalStatus.COMPLETED && goal.getStatus() != GoalStatus.COMPLETED;
        boolean restoring = status != null && status != GoalStatus.COMPLETED
                && goal.getStatus() == GoalStatus.COMPLETED;
        int affected = goalMapper.update(null, new LambdaUpdateWrapper<WishAiGoal>()
                .eq(WishAiGoal::getId, goalId)
                .eq(WishAiGoal::getVersion, version)
                .setSql("version = version + 1")
                .set(title != null, WishAiGoal::getTitle, title)
                .set(description != null, WishAiGoal::getDescription, description)
                .set(status != null, WishAiGoal::getStatus, status)
                // 首次完成事实不被重写；恢复时清理完成时间
                .set(completing, WishAiGoal::getCompletedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .set(restoring, WishAiGoal::getCompletedAt, null));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        return goalMapper.selectById(goalId);
    }

    /** 版本条件软删（T12）：删除与版本校验同一条 CAS，无检查-执行竞态。 */
    @Transactional
    public void deleteGoal(Long userId, Long goalId, Integer version) {
        WishAiGoal goal = requireOwnedGoal(userId, goalId);
        if (version == null || goal.getVersion() == null || !version.equals(goal.getVersion())) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
        int affected = goalMapper.update(null, new LambdaUpdateWrapper<WishAiGoal>()
                .eq(WishAiGoal::getId, goalId)
                .eq(WishAiGoal::getVersion, version)
                .isNull(WishAiGoal::getDeletedAt)
                .set(WishAiGoal::getDeletedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT, "目标已被并发修改，请刷新");
        }
    }

    /** 排序请求项：显式版本 CAS（含 version=0 的新建目标） */
    public record ReorderItem(Long goalId, Integer version, Integer sortOrder) {
    }

    /**
     * 批量排序（T12 重构）：锁父心愿行 → 全集/同心愿/唯一连续排序号校验 →
     * 逐项版本 CAS（version=0 也参与）→ 任一冲突抛异常整批回滚。
     */
    @Transactional
    public void reorder(Long userId, Long wishId, List<ReorderItem> items) {
        requireOwnedWish(userId, wishId);
        // 锁父心愿行：与新增/删除步骤竞争时整批校验在同一快照上进行
        Wish locked = wishMapper.selectByIdForUpdate(wishId);
        if (locked == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        List<WishAiGoal> existing = goalMapper.selectList(new LambdaQueryWrapper<WishAiGoal>()
                .eq(WishAiGoal::getWishId, wishId)
                .isNull(WishAiGoal::getDeletedAt));
        Set<Long> existingIds = existing.stream().map(WishAiGoal::getId).collect(Collectors.toSet());
        if (items == null || items.isEmpty() || existingIds.size() != items.size()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "排序集合必须与当前目标集合完全一致");
        }
        Set<Long> itemIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (ReorderItem item : items) {
            if (item == null || item.goalId() == null || !existingIds.contains(item.goalId())
                    || item.version() == null || item.version() < 0 || item.sortOrder() == null) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                        "排序项非法（须为当前心愿目标，版本与排序号必填）");
            }
            if (!itemIds.add(item.goalId()) || !sortOrders.add(item.sortOrder())) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                        "排序项不得重复（goalId 与 sortOrder 均须唯一）");
            }
        }
        if (sortOrders.stream().min(Integer::compareTo).orElse(0) != 0
                || sortOrders.stream().max(Integer::compareTo).orElse(0) != items.size() - 1) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "排序号必须为 0 起连续整数（0..n-1）");
        }
        if (sortOrders.size() != items.size()) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "排序号重复");
        }
        java.util.Map<Long, WishAiGoal> byId = existing.stream()
                .collect(Collectors.toMap(WishAiGoal::getId, Function.identity()));
        for (ReorderItem item : items) {
            WishAiGoal goal = byId.get(item.goalId());
            if (!item.version().equals(goal.getVersion())) {
                // 任一冲突整批回滚（@Transactional）
                throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT,
                        "目标已被并发修改，请刷新后重新排序");
            }
            int affected = goalMapper.update(null, new LambdaUpdateWrapper<WishAiGoal>()
                    .eq(WishAiGoal::getId, item.goalId())
                    .eq(WishAiGoal::getVersion, item.version())
                    .set(WishAiGoal::getSortOrder, item.sortOrder())
                    .setSql("version = version + 1"));
            if (affected == 0) {
                throw new BusinessException(WishErrorCodes.WISH_VERSION_CONFLICT,
                        "目标已被并发修改，请刷新后重新排序");
            }
        }
    }

    private WishAiGoal requireOwnedGoal(Long userId, Long goalId) {
        WishAiGoal goal = goalMapper.selectById(goalId);
        if (goal == null || goal.getDeletedAt() != null || !goal.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "目标不存在");
        }
        return goal;
    }
}
