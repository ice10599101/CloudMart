package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetUserGuardService;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 托管应用服务（R12/R27）：照顾事务 + 托管生命周期（启动/状态/结束）。
 *
 * <p>照顾语义（§16.3）：照顾只恢复状态、不产出经验/星光/亲密度/任务进度（不包装挂机收益）；
 * 结束/到期先结算照顾再转终态：关页面/离线不丢已应得的照顾（T18 最后一段不丢失）；
 * 结束后不再照顾（BE-09）。启动语义（R12）：先取用户守卫锁再复验互斥，
 * 锁内经统一互斥 Bean 复验活动与托管，跨表排他成立。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetCustodyCareService {

    private final PetMapper petMapper;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetClock petClock;
    private final PetProperties properties;
    private final PetUserGuardService guardService;
    private final PetActivityMutex activityMutex;

    /** 启动托管：每自然周 1 次（uk 幂等）；不收费不自动续。R12：守卫锁内复验互斥。 */
    @Transactional
    public Map<String, Object> startCustody(Long userId) {
        if (!properties.getFeatureSwitches().isCustody()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
        Pet pet = requireActivePet(userId);
        guardService.lockGuard(userId);
        // BE-09/R12：托管占用统一长期活动名额——锁内重读（打工/读书/捞瓶/休息/进行中托管互斥）
        activityMutex.requireFree(userId);
        LocalDate weekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        PetCustodyRecord record = new PetCustodyRecord();
        record.setUserId(userId);
        record.setPetId(pet.getId());
        record.setWeekStart(weekStart);
        record.setStatus("ACTIVE");
        record.setStartedAt(petClock.nowUtc());
        record.setEndsAt(petClock.nowUtc().plusHours(24));
        record.setRuleSnapshot(PetJsonUtils.toJson(Map.of(
                "hunger", Map.of("threshold", 30, "restoreTo", 50, "maxTimes", 2),
                "cleanliness", Map.of("threshold", 30, "restoreTo", 50, "maxTimes", 1))));
        record.setCareFeedUsed(0);
        record.setCareCleanUsed(0);
        try {
            custodyMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周托管次数已用完或正在托管中");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("custodyId", record.getId());
        result.put("endsAt", record.getEndsAt());
        return result;
    }

    /** 托管状态（惰性应用照顾：按原时间轴分段判定，唯一照顾事件去重） */
    public Map<String, Object> custodyStatus(Long userId) {
        PetCustodyRecord record = activeRecord(userId);
        Map<String, Object> result = new HashMap<>();
        if (record == null) {
            result.put("active", false);
            result.put("weekUsed", custodyMapper.selectCount(new LambdaQueryWrapper<PetCustodyRecord>()
                    .eq(PetCustodyRecord::getUserId, userId)
                    .eq(PetCustodyRecord::getWeekStart, petClock.businessDate().with(DayOfWeek.MONDAY))) > 0);
            return result;
        }
        // BE-09/R12：到期先结算最后一段照顾再原子结束（T18 最后一段不丢失），结束后不再照顾
        if (record.getEndsAt() != null && record.getEndsAt().isBefore(petClock.nowUtc())) {
            settleAndEnd(record);
            result.put("active", false);
            result.put("weekUsed", true);
            result.put("nextAvailableAt", petClock.businessDate().with(DayOfWeek.MONDAY).plusWeeks(1));
            return result;
        }
        applyCare(record);
        result.put("active", true);
        result.put("endsAt", record.getEndsAt());
        result.put("careFeedUsed", record.getCareFeedUsed());
        result.put("careCleanUsed", record.getCareCleanUsed());
        return result;
    }

    /** 提前结束（R12：先结算截止当前时刻的照顾再转终态；不退还本周次数） */
    @Transactional
    public void endCustody(Long userId) {
        PetCustodyRecord record = activeRecord(userId);
        if (record != null) {
            settleAndEnd(record);
        }
    }

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

    private Pet requireActivePet(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        return pet;
    }
}
