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
import java.util.List;

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
        LIKE_REWARD, MINIGAME, DECORATE, HOST_CARE, HOME_ENTER, BATTLE_DEFEAT_TARGET,
        /** 对战放弃/被拒/过期计数（P1-3：24h 内 ≥3 次进入发起冷却，仅计数无上限） */
        BATTLE_ABORT,
        /** 内容举报每日提交上限（R05：防刷举报队列；系统自动举报不计入） */
        REPORT,
        /** R04：PVE 胜利收益日配额（独立于串门 VISIT_REWARD；配额外仅参与不发币） */
        PVE_REWARD,
        /** P1-9：串门全局日上限（不同目标合计；单目标冷却另由 visit_fact 唯一键约束） */
        VISIT
    }

    private final PetDailyQuotaMapper quotaMapper;
    private final PetClock petClock;
    private final com.cloudmart.pet.config.PetMetrics metrics;

    public PetQuotaService(PetDailyQuotaMapper quotaMapper, PetClock petClock,
                           com.cloudmart.pet.config.PetMetrics metrics) {
        this.quotaMapper = quotaMapper;
        this.petClock = petClock;
        this.metrics = metrics;
    }

    /**
     * PET-02：配额业务日固定 {@code pet.businessZone}（默认 Asia/Shanghai，WISH-01 同口径）——
     * 不再依赖远程用户时区（原 UserPetTimezoneResolver 已删）：换时区/上报/Feign 故障
     * 均不改变每日上限归属；23:59 占用、次日释放按原凭据退回原日。
     */
    private LocalDate businessDate() {
        return petClock.businessDate();
    }

    /**
     * 原子占用一个名额。占满返回 false（调用方转无收益互动，PET_QUOTA_EXHAUSTED 语义）。
     */
    public boolean tryConsume(Long userId, QuotaType type, long targetId, int limit) {
        if (limit <= 0) {
            return false;
        }
        LocalDate businessDate = businessDate();
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
                metrics.increment("pet_quota_rejected", "type", type.name());
            }
            return retry > 0;
        }
    }

    /**
     * PET-02：占用凭据——granted 为 false 时 businessDate 仍填充（诊断用），
     * 凭据只可用于释放本次占用的同一行。
     */
    public record QuotaReservation(Long userId, QuotaType type, long targetId,
                                   LocalDate businessDate, boolean granted) {
    }

    /** 带凭据占用：调用方业务校验失败时用原凭据 {@link #release(QuotaReservation)} 回退 */
    public QuotaReservation tryConsumeReserved(Long userId, QuotaType type, long targetId, int limit) {
        boolean granted = tryConsume(userId, type, targetId, limit);
        return new QuotaReservation(userId, type, targetId, businessDate(), granted);
    }

    /**
     * PET-02：按原占用凭据释放——禁止跨零点退到新一天（旧实现按释放时当天查行，
     * 23:59 占用次日校验失败会把新一天的 used 减 1，而旧日占用永久丢失）。
     */
    public void release(QuotaReservation reservation) {
        if (!reservation.granted()) {
            return;
        }
        quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                .setSql("used = GREATEST(used - 1, 0)")
                .eq(PetDailyQuota::getUserId, reservation.userId())
                .eq(PetDailyQuota::getQuotaType, reservation.type().name())
                .eq(PetDailyQuota::getTargetId, reservation.targetId())
                .eq(PetDailyQuota::getBusinessDate, reservation.businessDate()));
    }

    /**
     * @deprecated 过渡兼容：按释放时当天释放，存在跨零点退错日的窗口——
     * 占用-释放配对请改用 {@link #tryConsumeReserved} + {@link #release(QuotaReservation)}。
     */
    @Deprecated
    public void release(Long userId, QuotaType type, long targetId) {
        quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                .setSql("used = GREATEST(used - 1, 0)")
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getQuotaType, type.name())
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, businessDate()));
    }

    /**
     * 仅计数（无上限语义，P1-3）：用于"放弃对战次数"这类软限制累计——
     * 计数永远成功（达上限与否由调用方读 {@link #used} 判断），失败不阻断主流程。
     */
    public void record(Long userId, QuotaType type, long targetId) {
        try {
            int updated = quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                    .setSql("used = used + 1")
                    .eq(PetDailyQuota::getUserId, userId)
                    .eq(PetDailyQuota::getQuotaType, type.name())
                    .eq(PetDailyQuota::getTargetId, targetId)
                    .eq(PetDailyQuota::getBusinessDate, businessDate()));
            if (updated == 0) {
                PetDailyQuota row = new PetDailyQuota();
                row.setUserId(userId);
                row.setQuotaType(type.name());
                row.setTargetId(targetId);
                row.setBusinessDate(businessDate());
                row.setUsed(1);
                try {
                    quotaMapper.insert(row);
                } catch (DuplicateKeyException concurrent) {
                    quotaMapper.update(null, new LambdaUpdateWrapper<PetDailyQuota>()
                            .setSql("used = used + 1")
                            .eq(PetDailyQuota::getUserId, userId)
                            .eq(PetDailyQuota::getQuotaType, type.name())
                            .eq(PetDailyQuota::getTargetId, targetId)
                            .eq(PetDailyQuota::getBusinessDate, businessDate()));
                }
            }
        } catch (Exception e) {
            log.warn("配额计数失败（软限制不阻断）: userId={}, type={}", userId, type, e);
        }
    }

    /** 已用次数（额度展示 rewardRemainingToday 用；无行返回 0） */
    public int used(Long userId, QuotaType type, long targetId) {
        PetDailyQuota row = quotaMapper.selectOne(new LambdaQueryWrapper<PetDailyQuota>()
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getQuotaType, type.name())
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, businessDate()));
        return row != null && row.getUsed() != null ? row.getUsed() : 0;
    }

    /**
     * 同一用户业务日、同一 targetId 下多类型配额已用次数的批量读（单次 IN 查询）。
     *
     * <p>用于 bootstrap 等需要同时展示多个额度余量的聚合读，替代逐类型
     * {@link #used} 产生的 N 次单行 selectOne；无行的类型按 0 返回。
     * 调用方不得传入空类型集合（SQL IN 空集非法）。</p>
     */
    public java.util.Map<QuotaType, Integer> usedBatch(Long userId,
                                                        java.util.Collection<QuotaType> types,
                                                        long targetId) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("usedBatch types 不能为空");
        }
        java.util.Map<QuotaType, Integer> result = new java.util.EnumMap<>(QuotaType.class);
        types.forEach(type -> result.put(type, 0));
        List<PetDailyQuota> rows = quotaMapper.selectList(new LambdaQueryWrapper<PetDailyQuota>()
                .eq(PetDailyQuota::getUserId, userId)
                .eq(PetDailyQuota::getTargetId, targetId)
                .eq(PetDailyQuota::getBusinessDate, businessDate())
                .in(PetDailyQuota::getQuotaType,
                        types.stream().map(QuotaType::name).toList()));
        for (PetDailyQuota row : rows) {
            QuotaType type = QuotaType.valueOf(row.getQuotaType());
            result.put(type, row.getUsed() != null ? row.getUsed() : 0);
        }
        return result;
    }

    /** 今日剩余额度（额度耗尽返回 0） */
    public int remaining(Long userId, QuotaType type, long targetId, int limit) {
        return Math.max(0, limit - used(userId, type, targetId));
    }
}
