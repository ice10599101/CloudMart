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
 * 托管照顾事务应用服务（R12/§16.3 托管语义）：公开事务方法——原实现是
 * private {@code @Transactional applyCustodyCare}，被同类非事务方法自调用，
 * 代理不生效（宠物效果与计数可分开提交）。照顾只恢复状态、不产出经验/星光/
 * 亲密度/任务进度（不包装挂机收益）。
 *
 * <p>结束/到期先结算照顾再转终态：关页面/离线不丢已应得的照顾（T18 最后一段不丢失）；
 * 结束后不再照顾（BE-09）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetCustodyCareService {

    private final PetMapper petMapper;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetClock petClock;

    /** 照顾效果（阈值触发、有限次数）：宠物属性恢复与照顾计数同事务提交 */
    @Transactional
    public void applyCare(PetCustodyRecord record) {
        if (record.getEndsAt() != null && record.getEndsAt().isBefore(petClock.nowUtc())) {
            return;
        }
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

    /**
     * 结算最后一段照顾并原子结束（CAS ACTIVE→ENDED，幂等；本周名额不恢复、不产出奖励）。
     * 主动结束与到期结束共用此路径——照顾不因"没打开页面"消失，结束后不再照顾。
     */
    @Transactional
    public void settleAndEnd(PetCustodyRecord record) {
        applyCare(record);
        int updated = custodyMapper.update(null, new LambdaUpdateWrapper<PetCustodyRecord>()
                .set(PetCustodyRecord::getStatus, "ENDED")
                .set(PetCustodyRecord::getEndedAt, petClock.nowUtc())
                .eq(PetCustodyRecord::getId, record.getId())
                .eq(PetCustodyRecord::getStatus, "ACTIVE"));
        if (updated > 0) {
            log.info("托管结束（照顾已结算）, custodyId={}, userId={}", record.getId(), record.getUserId());
        }
    }

    /** 当前生效中的托管（无则 null） */
    public PetCustodyRecord activeRecord(Long userId) {
        return custodyMapper.selectOne(new LambdaQueryWrapper<PetCustodyRecord>()
                .eq(PetCustodyRecord::getUserId, userId)
                .eq(PetCustodyRecord::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }
}
