package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 托管照顾事务工作器（PET-08）：照顾与结算转终态的<strong>真实事务边界</strong>在这里。
 *
 * <p>必须独立于 {@link PetCustodyCareService} 成 Bean：custodyStatus（无事务）同类自调用
 * applyCare/settleAndEnd，{@code @Transactional} 经 Spring 代理不生效——宠物属性恢复与
 * 照顾计数是两条独立自动提交的写，进程中断可留半成功。工作器只做事务内原子写；
 * 生命周期编排（状态查询/提前结束/惰性清理）留在应用服务。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetCustodyCareTxWorker {

    private final PetMapper petMapper;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetClock petClock;

    /**
     * 中途照顾（阈值触发、有限次数）：宠物属性恢复与照顾计数同事务提交。
     * 不判是否到期——到期前的惰性照顾由调用方在未到期分支调用（PET-08）。
     */
    @Transactional
    public void applyCareInTx(PetCustodyRecord record) {
        applyCareQuota(record);
    }

    /**
     * 结算最后一段照顾并原子结束（PET-08/T18）：到期记录不再被"endsAt 已过直接返回"跳过
     * 最终照顾——先应用照顾再 CAS ACTIVE→ENDED，同事务提交；CAS 幂等，重复执行零副作用。
     * 本周名额不恢复、不产出奖励（照顾不包装挂机收益）。
     */
    @Transactional
    public void settleAndEndInTx(PetCustodyRecord record) {
        applyCareQuota(record);
        casEnd(record);
    }

    /**
     * PET-08：该用户已到期未收尾的托管幂等收尾——互斥玩法启动前惰性调用，
     * 不要求用户先打开托管状态页才能恢复正常玩法；加入调用方事务（守卫锁内）。
     */
    @Transactional
    public void settleExpiredForUser(Long userId) {
        PetCustodyRecord record = custodyMapper.selectOne(new LambdaQueryWrapper<PetCustodyRecord>()
                .eq(PetCustodyRecord::getUserId, userId)
                .eq(PetCustodyRecord::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (record != null && record.getEndsAt() != null
                && record.getEndsAt().isBefore(petClock.nowUtc())) {
            settleAndEndInTx(record);
        }
    }

    /** 照顾效果：饥饿/清洁低于阈值恢复到 50，受规则次数上限约束（与 ruleSnapshot 语义一致） */
    private void applyCareQuota(PetCustodyRecord record) {
        Pet pet = petMapper.selectById(record.getPetId());
        if (pet == null) {
            return;
        }
        LambdaUpdateWrapper<Pet> wrapper = new LambdaUpdateWrapper<Pet>().eq(Pet::getId, pet.getId());
        boolean changed = false;
        if (pet.getHunger() < 30 && record.getCareFeedUsed() < 2) {
            wrapper.setSql("hunger = 50");
            record.setCareFeedUsed(record.getCareFeedUsed() + 1);
            changed = true;
        }
        if (pet.getCleanliness() < 30 && record.getCareCleanUsed() < 1) {
            wrapper.setSql("cleanliness = 50");
            record.setCareCleanUsed(record.getCareCleanUsed() + 1);
            changed = true;
        }
        if (changed) {
            petMapper.update(null, wrapper);
            custodyMapper.updateById(record);
        }
    }

    /** CAS ACTIVE→ENDED（幂等）：并发状态查询/结束只有一个胜者 */
    private void casEnd(PetCustodyRecord record) {
        int updated = custodyMapper.update(null, new LambdaUpdateWrapper<PetCustodyRecord>()
                .set(PetCustodyRecord::getStatus, "ENDED")
                .set(PetCustodyRecord::getEndedAt, petClock.nowUtc())
                .eq(PetCustodyRecord::getId, record.getId())
                .eq(PetCustodyRecord::getStatus, "ACTIVE"));
        if (updated > 0) {
            log.info("托管结束（照顾已结算）, custodyId={}, userId={}", record.getId(), record.getUserId());
        }
    }
}
