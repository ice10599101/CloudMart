package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户级长期活动统一互斥（R12/§5 忙碌规则）：工作/读书/职业工作/捞瓶/休息/托管
 * 共用同一"进行中名额"——pet_activity(IN_PROGRESS) 与 pet_custody_record(ACTIVE)
 * 跨表排他，任何开始入口都必须经此检查，不允许各入口自行拼条件（原实现
 * ensureNoBusyActivity 只查 pet_activity，托管开始先检查后加锁，双向都有竞态窗口）。
 *
 * <p>数据库层兜底：uk_activity_user_active_v2（活动唯一）+ 本类检查在调用方事务/守卫锁内执行；
 * startCustody 在 lockGuard 之后调用本类复验，锁内重读消除 check-then-act 竞态。</p>
 */
@Component
@RequiredArgsConstructor
public class PetActivityMutex {

    private final PetActivityMapper activityMapper;
    private final PetCustodyRecordMapper custodyMapper;
    /** PET-08：互斥判定前先把用户已到期的托管幂等收尾（不要求先打开托管状态页） */
    private final PetCustodyCareTxWorker custodyCareTxWorker;

    /** @return 该用户是否有进行中的长期活动（打工/读书/职业/捞瓶/休息） */
    public boolean hasBusyActivity(Long userId) {
        return activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())) > 0;
    }

    /** @return 该用户是否有生效中的托管 */
    public boolean hasActiveCustody(Long userId) {
        return custodyMapper.selectCount(new LambdaQueryWrapper<PetCustodyRecord>()
                .eq(PetCustodyRecord::getUserId, userId)
                .eq(PetCustodyRecord::getStatus, "ACTIVE")) > 0;
    }

    /**
     * 开始长期活动前的互斥复验（R12：工作/读书/职业/捞瓶/休息与托管互斥）。
     * 必须在用户守卫锁/事务内调用——锁内重读，并发开始只有一个能通过。
     * PET-08：到期 ACTIVE 托管先结算收尾再判互斥——"到期后直接打工可成功"，
     * 收尾事务加入调用方事务/守卫锁，不存在互斥窗口。
     */
    public void requireFree(Long userId) {
        if (hasBusyActivity(userId)) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "宠物一次只能做一件事，等当前任务结束吧");
        }
        custodyCareTxWorker.settleExpiredForUser(userId);
        if (hasActiveCustody(userId)) {
            throw new BusinessException(PetErrorCodes.PET_USER_BUSY,
                    "宠物正在托管中，托管与任务不能同时进行");
        }
    }
}
