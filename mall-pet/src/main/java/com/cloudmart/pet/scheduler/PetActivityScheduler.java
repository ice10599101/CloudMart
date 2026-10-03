package com.cloudmart.pet.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetActivityService;
import com.cloudmart.pet.service.PetBattleService;
import com.cloudmart.pet.service.PetBottleFishingService;
import com.cloudmart.pet.service.PetInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 宠物活动定时器（服务端时间是唯一权威——原文档 §9/§17：用户关闭 App 也不影响任务完成）。
 *
 * <p>分钟级扫描：到时任务惰性流转 + 推送"任务完成"通知
 * （打工"我的打工结束啦，快来领取奖励！" / 读书"我已经读完啦！"；
 * 捞瓶在 settle 内按实际结果推送）。领取 CAS 幂等，扫描重复执行无副作用。</p>
 * <p>小时级清扫：超期未领取（72h）作废 + 超期未应战对战（48h）过期。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PetActivityScheduler {

    private static final int SCAN_BATCH = 100;
    /** R25：本实例持有的锁 token（key→token），释放时比较删除 */
    private final java.util.Map<String, String> LOCK_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /** P1-5：多实例互斥锁（抢不到直接返回；Redis 故障 Fail-Open 退化为无害重扫） */
    static final String LOCK_SETTLE = "pet:lock:activity-settle";
    static final String LOCK_HOUSEKEEPING = "pet:lock:activity-housekeeping";
    static final String LOCK_RANK_REBUILD = "pet:lock:rank-rebuild";

    private final PetActivityMapper activityMapper;
    private final PetMapper petMapper;
    private final PetBottleFishingService bottleFishingService;
    private final PetActivityService activityService;
    private final PetBattleService battleService;
    private final PetInteractionService interactionService;
    private final PetEventProducer eventProducer;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.cloudmart.pet.service.PetRankingService rankingService;
    private final com.cloudmart.pet.service.impl.PetDashboardSnapshotService dashboardSnapshotService;
    private final com.cloudmart.pet.service.impl.PetSeasonSettlementService seasonSettlementService;
    private final com.cloudmart.pet.service.impl.PetFriendFeedService friendFeedService;
    /** R15：完成 CAS 与 Outbox 登记同一事务——kill 窗口不再丢"任务完成"通知 */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void settleFinishedActivities() {
        if (!tryLock(LOCK_SETTLE, Duration.ofSeconds(55))) {
            return;
        }
        try {
            List<PetActivity> finished = activityMapper.selectList(new LambdaQueryWrapper<PetActivity>()
                    .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                    .le(PetActivity::getFinishedAt, LocalDateTime.now(ZoneId.of("UTC")))
                    // P1-5：确定性排序——最老到期先结算，保证批处理推进顺序可复现
                    .orderByAsc(PetActivity::getFinishedAt)
                    .orderByAsc(PetActivity::getId)
                    .last("LIMIT " + SCAN_BATCH));
            for (PetActivity activity : finished) {
                try {
                    handleFinished(activity);
                } catch (Exception e) {
                    // 单条失败不阻断批处理（下轮扫描重试；CAS 保证幂等）
                    log.error("活动结算失败: activityId={}, type={}", activity.getId(), activity.getActivityType(), e);
                }
            }
        } finally {
            unlock(LOCK_SETTLE);
        }
    }

    @Scheduled(cron = "0 30 * * * *", zone = "UTC")
    public void housekeeping() {
        if (!tryLock(LOCK_HOUSEKEEPING, Duration.ofSeconds(300))) {
            return;
        }
        try {
            int expiredClaims = activityService.expireStaleClaims();
            int expiredBattles = battleService.expirePendingBattles();
            // P2-3：看板当日快照小时级增量写入（历史日冻结，看板读快照 + 当日实时合并）
            dashboardSnapshotService.writeTodaySnapshot();
            // F2：赛季到期结算（CAS 防重，失败回退 ACTIVE 下轮重试）
            seasonSettlementService.settleExpiredSeasons();
            if (expiredClaims > 0 || expiredBattles > 0) {
                log.info("宠物清扫完成: expiredClaims={}, expiredBattles={}", expiredClaims, expiredBattles);
            }
        } catch (Exception e) {
            log.error("宠物清扫任务失败", e);
        } finally {
            unlock(LOCK_HOUSEKEEPING);
        }
    }

    /** P1-4：排行榜缓存每日全量重建（校准 ZSet 漂移；Redis 异常由重建内部降级） */
    @Scheduled(cron = "0 20 3 * * *", zone = "UTC")
    public void rebuildRankingCache() {
        if (!tryLock(LOCK_RANK_REBUILD, Duration.ofSeconds(600))) {
            return;
        }
        try {
            boolean rebuilt = rankingService.rebuildRankingCache();
            log.info("排行榜缓存重建: result={}", rebuilt ? "OK" : "SKIPPED（降级）");
        } catch (Exception e) {
            log.error("排行榜缓存重建失败", e);
        } finally {
            unlock(LOCK_RANK_REBUILD);
        }
    }

    /** SET NX EX 抢锁（携带随机 token）；Redis 故障 Fail-Open（返回 true 继续执行，靠下游 CAS 幂等兜底） */
    private boolean tryLock(String key, Duration ttl) {
        try {
            String token = java.util.UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (!Boolean.FALSE.equals(acquired)) {
                LOCK_TOKENS.put(key, token);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("调度分布式锁不可用（Fail-Open 继续执行）: key={}", key, e);
            return true;
        }
    }

    /**
     * R25：Lua 比较删除——仅当锁值仍为本执行者 token 才删除。
     * 原实现固定值 "1" 直接 delete：长任务超 TTL 后，旧持有者 finally 会删掉
     * 新持有者刚抢到的锁，互斥窗口失效（T40 调度锁有效性）。
     */
    private void unlock(String key) {
        try {
            String token = LOCK_TOKENS.remove(key);
            if (token == null) {
                return;
            }
            redisTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class), java.util.List.of(key), token);
        } catch (Exception ignored) {
            // TTL 兜底过期，无需处理
        }
    }

    private void handleFinished(PetActivity activity) {
        PetActivityType type = PetActivityType.valueOf(activity.getActivityType());
        switch (type) {
            case WORK, STUDY, CAREER_WORK -> transactionTemplate.executeWithoutResult(status -> {
                // R15：CAS 与 outbox 登记同事务提交——进程 kill 不再产生"已完成但通知永久丢失"
                int updated = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                        .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                        .eq(PetActivity::getId, activity.getId())
                        .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
                if (updated > 0) {
                    publishCompleted(activity, type);
                }
            });
            case BOTTLE_FISHING -> bottleFishingService.settle(activity.getUserId(), activity.getId());
            // R30：REST/BOTTLE 结算按 activityId 归属到 activity.petId，效果不再落到当前主宠
            case REST -> interactionService.settleRest(activity.getUserId(), activity.getId());
            case FEED, PLAY, CLEAN, VISIT, EVOLVE -> {
                // 即时行为不存在 IN_PROGRESS 状态，正常不会扫到；防御性日志
                log.warn("扫描到非预期进行中活动: activityId={}, type={}", activity.getId(), type);
            }
        }
    }

    private void publishCompleted(PetActivity activity, PetActivityType type) {
        Pet pet = petMapper.selectById(activity.getPetId());
        String petName = pet != null ? pet.getName() : "宠物";
        // F3：打工/读书完成动态（CAS 已保证仅首个结算者推送，扇出同样单次）
        friendFeedService.append(activity.getUserId(), activity.getPetId(),
                type == PetActivityType.STUDY
                        ? com.cloudmart.pet.service.impl.PetFriendFeedService.EVENT_STUDY_COMPLETED
                        : com.cloudmart.pet.service.impl.PetFriendFeedService.EVENT_WORK_COMPLETED,
                petName + " " + (type == PetActivityType.STUDY ? "读完书啦！" : "打工归来！"), petName);
        if (type == PetActivityType.WORK) {
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_WORK_COMPLETED, new PetEventProducer.PetEventMessage(
                    "WORK_COMPLETED:" + activity.getId(),
                    String.valueOf(activity.getUserId()), "PET_WORK_COMPLETED",
                    "我的打工结束啦！",
                    petName + "：" + "主人，我打工回来啦，快来领取奖励！", String.valueOf(activity.getId()), "PET_WORK_COMPLETED"),
                    activity.getPetId());
        } else if (type == PetActivityType.CAREER_WORK) {
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_WORK_COMPLETED, new PetEventProducer.PetEventMessage(
                    "CAREER_WORK_COMPLETED:" + activity.getId(),
                    String.valueOf(activity.getUserId()), "PET_WORK_COMPLETED",
                    "我的工作结束啦！",
                    petName + "：" + "主人，今天的工作做完啦，工钱还没领呢～", String.valueOf(activity.getId()), "PET_WORK_COMPLETED"),
                    activity.getPetId());
        } else {
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_STUDY_COMPLETED, new PetEventProducer.PetEventMessage(
                    "STUDY_COMPLETED:" + activity.getId(),
                    String.valueOf(activity.getUserId()), "PET_STUDY_COMPLETED",
                    "我已经读完啦！",
                    petName + "：" + "主人，这本书读完啦，我感觉自己变聪明了一点点！", String.valueOf(activity.getId()), "PET_STUDY_COMPLETED"),
                    activity.getPetId());
        }
    }
}
