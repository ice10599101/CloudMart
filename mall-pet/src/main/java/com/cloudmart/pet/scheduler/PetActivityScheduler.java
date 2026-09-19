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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final PetActivityMapper activityMapper;
    private final PetMapper petMapper;
    private final PetBottleFishingService bottleFishingService;
    private final PetActivityService activityService;
    private final PetBattleService battleService;
    private final PetEventProducer eventProducer;

    @Scheduled(fixedDelay = 60_000)
    public void settleFinishedActivities() {
        List<PetActivity> finished = activityMapper.selectList(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .le(PetActivity::getFinishedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .last("LIMIT " + SCAN_BATCH));
        for (PetActivity activity : finished) {
            try {
                handleFinished(activity);
            } catch (Exception e) {
                // 单条失败不阻断批处理（下轮扫描重试；CAS 保证幂等）
                log.error("活动结算失败: activityId={}, type={}", activity.getId(), activity.getActivityType(), e);
            }
        }
    }

    @Scheduled(cron = "0 30 * * * *")
    public void housekeeping() {
        try {
            int expiredClaims = activityService.expireStaleClaims();
            int expiredBattles = battleService.expirePendingBattles();
            if (expiredClaims > 0 || expiredBattles > 0) {
                log.info("宠物清扫完成: expiredClaims={}, expiredBattles={}", expiredClaims, expiredBattles);
            }
        } catch (Exception e) {
            log.error("宠物清扫任务失败", e);
        }
    }

    private void handleFinished(PetActivity activity) {
        PetActivityType type = PetActivityType.valueOf(activity.getActivityType());
        switch (type) {
            case WORK, STUDY -> {
                int updated = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                        .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                        .eq(PetActivity::getId, activity.getId())
                        .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
                if (updated > 0) {
                    publishCompleted(activity, type);
                }
            }
            case BOTTLE_FISHING -> bottleFishingService.settle(activity.getUserId());
            case REST, FEED, PLAY, CLEAN -> {
                // 即时行为不存在 IN_PROGRESS 状态，正常不会扫到；防御性日志
                log.warn("扫描到非预期进行中活动: activityId={}, type={}", activity.getId(), type);
            }
        }
    }

    private void publishCompleted(PetActivity activity, PetActivityType type) {
        Pet pet = petMapper.selectById(activity.getPetId());
        String petName = pet != null ? pet.getName() : "宠物";
        if (type == PetActivityType.WORK) {
            eventProducer.publish(RocketMQConfig.PET_TAG_WORK_COMPLETED, new PetEventProducer.PetEventMessage(
                    activity.getUserId(), "PET_WORK_COMPLETED",
                    "我的打工结束啦！",
                    petName + "：" + "主人，我打工回来啦，快来领取奖励！", activity.getId(), "PET_WORK_COMPLETED"));
        } else {
            eventProducer.publish(RocketMQConfig.PET_TAG_STUDY_COMPLETED, new PetEventProducer.PetEventMessage(
                    activity.getUserId(), "PET_STUDY_COMPLETED",
                    "我已经读完啦！",
                    petName + "：" + "主人，这本书读完啦，我感觉自己变聪明了一点点！", activity.getId(), "PET_STUDY_COMPLETED"));
        }
    }
}
