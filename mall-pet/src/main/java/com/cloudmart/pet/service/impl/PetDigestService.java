package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetOfflineCursor;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetDiaryEntryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetOfflineCursorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线摘要应用服务（R27 从 PetPlayFeatureService 拆分）：
 * 按上次确认游标聚合离线期间事实，查询不重发奖励、不推进游标；
 * 确认仅推进游标（R40：原子 MAX，未来值拒绝）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetDigestService {

    private final PetMapper petMapper;
    private final PetClock petClock;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetOfflineCursorMapper offlineCursorMapper;
    private final com.cloudmart.pet.repository.PetActivityMapper activityMapper;
    private final com.cloudmart.pet.repository.PetDiaryEntryMapper diaryEntryMapper;

    // ---------------- N05 离线摘要 ----------------

    /** 首次查询默认回溯窗口（小时）：无游标时按 maxIdleHours 口径回溯 */
    private static final long DIGEST_DEFAULT_LOOKBACK_HOURS = 48;

    /**
     * N05 离线摘要：按上次确认游标聚合离线期间的事实（完成任务/待领取/来访/里程碑），
     * 查询不重发奖励、不推进游标。
     */
    public Map<String, Object> offlineDigest(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        java.time.LocalDateTime now = petClock.nowUtc();
        java.time.LocalDateTime from = cursor != null ? cursor.getLastConfirmedAt()
                : now.minusHours(DIGEST_DEFAULT_LOOKBACK_HOURS);
        // BE-10：固定摘要上界 throughAt——确认只推进到该点；阅读期间的新事件下轮可见
        java.time.LocalDateTime throughAt = now;

        // 完成=已领取（CLAIMED），待领=完成未领（COMPLETED）——不再共用同一条件（BE-10）
        Long finished = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "CLAIMED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long claimable = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "COMPLETED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long visits = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, com.cloudmart.pet.enums.PetActivityType.VISIT.name())
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long milestones = diaryEntryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetDiaryEntry>()
                .eq(com.cloudmart.pet.entity.PetDiaryEntry::getUserId, userId)
                .gt(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, from)
                .le(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, throughAt));

        Map<String, Object> result = new HashMap<>();
        result.put("offlineHours", java.time.Duration.between(from, throughAt).toHours());
        result.put("from", from);
        result.put("throughAt", throughAt);
        result.put("petState", Map.of("level", pet.getLevel(), "hunger", pet.getHunger(),
                "happiness", pet.getHappiness(), "energy", pet.getEnergy(), "cleanliness", pet.getCleanliness()));
        // §3.5：数量为 JSON number——计数显式转 int，避免 Long 被 ID 定制器连带字符串化
        result.put("finishedTasks", finished == null ? 0 : finished.intValue());
        result.put("claimableTasks", claimable == null ? 0 : claimable.intValue());
        result.put("visits", visits == null ? 0 : visits.intValue());
        result.put("milestones", milestones == null ? 0 : milestones.intValue());
        result.put("hasCursor", cursor != null);
        return result;
    }

    /**
     * N05 确认（BE-10）：只推进到摘要展示时的固定上界 throughAt（不取确认时刻的 now），
     * 且游标不倒退——阅读确认期间新发生的事件不会被越过，下次摘要仍可见。
     */
    @Transactional
    public Map<String, Object> confirmOfflineDigest(Long userId, java.time.LocalDateTime throughAt) {
        if (petMapper.selectCount(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)) == 0) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        java.time.LocalDateTime now = petClock.nowUtc();
        // R40：上界合法性——未来值拒绝（不伪装成 now）；未提供时游标不动
        //（原实现把缺失/未来值改为 now，会跳过尚未展示的事件，T76）
        if (throughAt != null && throughAt.isAfter(now)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "确认上界不能晚于当前时刻");
        }
        java.time.LocalDateTime target = throughAt;
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        if (cursor == null) {
            cursor = new com.cloudmart.pet.entity.PetOfflineCursor();
            cursor.setUserId(userId);
            cursor.setLastConfirmedAt(target);
            try {
                offlineCursorMapper.insert(cursor);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                cursor = offlineCursorMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                                .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId));
            }
        }
        // R40：游标原子推进——SQL 条件 MAX（并发确认不倒退，替代读-比-写竞态）
        if (target != null) {
            int advanced = offlineCursorMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                            .set(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)
                            .eq(com.cloudmart.pet.entity.PetOfflineCursor::getId, cursor.getId())
                            .and(w -> w.isNull(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt)
                                    .or().lt(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)));
            cursor.setLastConfirmedAt(advanced > 0 || cursor.getLastConfirmedAt() == null
                    ? target : cursor.getLastConfirmedAt());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("confirmedAt", cursor.getLastConfirmedAt());
        return result;
    }


}
