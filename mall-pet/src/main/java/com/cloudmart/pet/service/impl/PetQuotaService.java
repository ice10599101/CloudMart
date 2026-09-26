package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.entity.PetDailyQuota;
import com.cloudmart.pet.repository.PetDailyQuotaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 用户每日收益额度（B06/§2.4）：数据库权威，Redis 仅作展示性快速限频。
 *
 * <p>主体为用户——切换宠物、切换入口、多端提交不绕过；targetId 支持按目标细分
 * （PvP 对同一对手每天 1 场收益）。占用为原子条件更新（used &lt; limit 才递增）；
 * 业务校验失败需要回退时显式 {@link #release}，状态验证失败不永久吃掉额度。</p>
 */
@Component
@Slf4j
public class PetQuotaService {

    public enum QuotaType {
        FEED, PLAY_REWARD, REST_INTIMACY, BATTLE_REWARD, PVP_OPPONENT,
        WALL_POST, WALL_REPLY, VISIT_REWARD, FRIEND_VISIT_REWARD,
        LIKE_REWARD, MINIGAME, DECORATE, HOST_CARE
    }

    private final PetDailyQuotaMapper quotaMapper;
    private final PetClock petClock;

    public PetQuotaService(PetDailyQuotaMapper quotaMapper, PetClock petClock) {
        this.quotaMapper = quotaMapper;
        this.petClock = petClock;
    }

    /**
     * 原子占用一个名额。占满返回 false（调用方转无收益互动，PET_QUOTA_EXHAUSTED 语义）。
     */
    public boolean tryConsume(Long userId, QuotaType type, long targetId, int limit) {
        if (limit <= 0) {
            return false;
        }
        LocalDate businessDate = petClock.businessDate();
        int updated = quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                .setSql("used = used + 1")
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getQuotaType, type.name())
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, businessDate)
                .lt(PetDailyQuota::getUsed, limit));
        if (updated > 0) {
            return true;
        }
        try {
            PetDailyQuota row = new PetDailyQuota();
            row.setUserId(userId);
            row.setQuotaType(type.name());
            row.setTargetId(targetId);
            row.setBusinessDate(businessDate);
            row.setUsed(1);
            quotaMapper.insert(row);
            return limit >= 1;
        } catch (DuplicateKeyException concurrent) {
            // 行刚被并发创建：重试一次条件更新；仍失败说明额度已占满
            int retry = quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                    .setSql("used = used + 1")
                    .eq(PetDailyQuota::getUserId, userId)
                    .eq(PetDailyQuota::getQuotaType, type.name())
                    .eq(PetDailyQuota::getTargetId, targetId)
                    .eq(PetDailyQuota::getBusinessDate, businessDate)
                    .lt(PetDailyQuota::getUsed, limit));
            if (retry == 0) {
                log.debug("每日额度已占满, userId={}, type={}, target={}", userId, type, targetId);
            }
            return retry > 0;
        }
    }

    /** 释放一个已占用名额（业务校验失败/活动未成立回退；不将计数减到负数） */
    public void release(Long userId, QuotaType type, long targetId) {
        quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                .setSql("used = GREATEST(used - 1, 0)")
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getQuotaType, type.name())
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, petClock.businessDate()));
    }

    /** 已用次数（额度展示 rewardRemainingToday 用；无行返回 0） */
    public int used(Long userId, QuotaType type, long targetId) {
        PetDailyQuota row = quotaMapper.selectOne(new LambdaQueryWrapper<PetDailyQuota>()
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getQuotaType, type.name())
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, petClock.businessDate()));
        return row != null && row.getUsed() != null ? row.getUsed() : 0;
    }

    /** 今日剩余额度（额度耗尽返回 0） */
    public int remaining(Long userId, QuotaType type, long targetId, int limit) {
        return Math.max(0, limit - used(userId, type, targetId));
    }
}
