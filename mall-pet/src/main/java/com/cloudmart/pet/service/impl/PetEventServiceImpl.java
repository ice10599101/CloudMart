package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetEventConfig;
import com.cloudmart.pet.entity.PetEventProgress;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.enums.PetEventType;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetEventConfigMapper;
import com.cloudmart.pet.repository.PetEventProgressMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetEventService;
import com.cloudmart.pet.vo.PetEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 社区宠物活动实现。
 *
 * <p>进度统计口径（与 {@link PetEventType} 一一对应）：
 * BOTTLE→pet_bottle_record 全部流水（含空手而归，鼓励参与）、BATTLE→胜场数、
 * WORK/STUDY/FEED/PLAY/VISIT→pet_activity 行为留痕（CLAIMED/COMPLETED）。</p>
 *
 * <p>领奖顺序：<b>先标记领奖（幂等 CAS），再发奖励</b>——发奖失败（星光 Feign 503）
 * 抛异常整体回滚，标记一并撤销，用户可重试；重复点击只有一次能穿过 CAS。</p>
 */
@Service
@Slf4j
public class PetEventServiceImpl implements PetEventService {

    private final PetStateService stateService;
    private final PetEventConfigMapper eventConfigMapper;
    private final PetEventProgressMapper progressMapper;
    private final PetActivityMapper activityMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetBattleMapper battleMapper;
    private final PetInventoryMapper inventoryMapper;
    private final WishFeignClient wishFeignClient;
    private final PetOperationService operationService;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;

    public PetEventServiceImpl(PetStateService stateService,
                               PetEventConfigMapper eventConfigMapper,
                               PetEventProgressMapper progressMapper,
                               PetActivityMapper activityMapper,
                               PetBottleRecordMapper bottleRecordMapper,
                               PetBattleMapper battleMapper,
                               PetInventoryMapper inventoryMapper,
                               WishFeignClient wishFeignClient,
                               PetOperationService operationService,
                               PetAchievementService achievementService,
                               PetEventProducer eventProducer) {
        this.stateService = stateService;
        this.eventConfigMapper = eventConfigMapper;
        this.progressMapper = progressMapper;
        this.activityMapper = activityMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.battleMapper = battleMapper;
        this.inventoryMapper = inventoryMapper;
        this.wishFeignClient = wishFeignClient;
        this.operationService = operationService;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
    }

    @Override
    public List<PetEventVO> events(Long userId) {
        return eventsForPet(stateService.requireActivePet(userId));
    }

    @Override
    public List<PetEventVO> eventsForPet(Pet pet) {
        List<PetEventConfig> configs = eventConfigMapper.selectList(new LambdaQueryWrapper<PetEventConfig>()
                .eq(PetEventConfig::getEnabled, true)
                .orderByAsc(PetEventConfig::getSort));
        if (configs.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        return configs.stream()
                .filter(config -> inWindow(config, now))
                .map(config -> toVo(pet, config, now))
                .toList();
    }

    @Override
    @Transactional
    public PetEventVO claim(Long userId, String eventCode) {
        Pet pet = stateService.requireActivePet(userId);
        PetEventConfig config = eventConfigMapper.selectOne(new LambdaQueryWrapper<PetEventConfig>()
                .eq(PetEventConfig::getCode, eventCode)
                .eq(PetEventConfig::getEnabled, true)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "活动不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        // B16：开始前不能领；结束后 24h 内仍可领取已达成奖励
        requireClaimWindow(config, now);
        int progress = countProgress(pet, config);
        int target = config.getTargetValue() != null ? config.getTargetValue() : 1;
        if (progress < target) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FINISHED,
                    "还差 " + (target - progress) + " 次就能完成啦");
        }
        markClaimed(pet, config, progress, now);

        int expReward = orZero(config.getRewardExp());
        if (expReward > 0) {
            int levelups = stateService.grantExp(pet, expReward);
            if (levelups > 0) {
                achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
                eventProducer.publish(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                        "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel(),
                        String.valueOf(userId), "PET_LEVEL_UP",
                        "宠物升级啦！",
                        pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                        String.valueOf(pet.getId()), "PET_LEVEL_UP"));
            }
        }
        int starlight = orZero(config.getRewardStarlight());
        if (starlight > 0) {
            // 操作键绑定 (pet, event, claimDate)：同一活动多次领取只一次收益
            String operationId = operationService.operationKey("EVENT_CLAIM",
                    pet.getId(), config.getCode(), now.toLocalDate());
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, userId, pet.getId(), "EVENT_CLAIM", pet.getId(), starlight, null);
            if (!settlement.isCompleted()) {
                log.info("活动奖励星光结算中, eventCode={}, operationId={}", config.getCode(), operationId);
            }
        }
        grantRewardItem(pet, config.getRewardItemCode(), now);
        // 直接以本次领奖结果构建 VO：不再回读一次（避免读路径与写路径口径不一致）
        return buildVo(config, now, progress, now);
    }

    /** 领奖幂等：claimed_at 为标记；并发重复请求由条件更新/唯一键兜底 */
    private void markClaimed(Pet pet, PetEventConfig config, int progress, LocalDateTime now) {
        PetEventProgress existing = progressMapper.selectOne(new LambdaQueryWrapper<PetEventProgress>()
                .eq(PetEventProgress::getPetId, pet.getId())
                .eq(PetEventProgress::getEventCode, config.getCode())
                .last("LIMIT 1"));
        if (existing == null) {
            PetEventProgress created = new PetEventProgress();
            created.setPetId(pet.getId());
            created.setUserId(pet.getUserId());
            created.setEventCode(config.getCode());
            created.setProgress(progress);
            created.setClaimedAt(now);
            try {
                progressMapper.insert(created);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(PetErrorCodes.PET_EVENT_ALREADY_CLAIMED, "奖励已经领取过啦");
            }
            return;
        }
        if (existing.getClaimedAt() != null) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        int updated = progressMapper.update(null, new LambdaUpdateWrapper<PetEventProgress>()
                .set(PetEventProgress::getClaimedAt, now)
                .set(PetEventProgress::getProgress, progress)
                .eq(PetEventProgress::getId, existing.getId())
                .isNull(PetEventProgress::getClaimedAt));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
    }

    /** 活动奖励装备入包（已拥有则跳过，不报错——奖励宁多不少地留给用户） */
    private void grantRewardItem(Pet pet, String itemCode, LocalDateTime now) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        PetInventory item = new PetInventory();
        item.setPetId(pet.getId());
        item.setUserId(pet.getUserId());
        item.setItemType(PetItemType.EQUIPMENT.name());
        item.setItemCode(itemCode);
        item.setQuantity(1);
        item.setEquipped(false);
        item.setAcquiredAt(now);
        try {
            inventoryMapper.insert(item);
        } catch (DuplicateKeyException e) {
            log.debug("活动奖励物品已拥有，跳过入包: petId={}, item={}", pet.getId(), itemCode);
        }
    }

    private PetEventVO toVo(Pet pet, PetEventConfig config, LocalDateTime now) {
        PetEventProgress record = progressMapper.selectOne(new LambdaQueryWrapper<PetEventProgress>()
                .eq(PetEventProgress::getPetId, pet.getId())
                .eq(PetEventProgress::getEventCode, config.getCode())
                .last("LIMIT 1"));
        return buildVo(config, now, countProgress(pet, config),
                record != null ? record.getClaimedAt() : null);
    }

    /** 活动 VO 组装（读路径与领奖路径共用同一口径） */
    private PetEventVO buildVo(PetEventConfig config, LocalDateTime now, int progress, LocalDateTime claimedAt) {
        int target = config.getTargetValue() != null ? config.getTargetValue() : 1;
        boolean claimed = claimedAt != null;
        boolean completed = progress >= target;
        boolean claimable = completed && !claimed;
        boolean expired = !completed && config.getEndsAt() != null && now.isAfter(config.getEndsAt());
        return new PetEventVO(config.getCode(), config.getName(), config.getDescription(),
                config.getEventType(), target, progress, completed, claimable, claimed, expired,
                config.getRewardStarlight(), config.getRewardExp(), config.getRewardItemCode(),
                config.getStartsAt(), config.getEndsAt(), claimedAt);
    }

    /** 进度统计（惰性，不落计数器）：按统计口径 COUNT 既有业务表 */
    int countProgress(Pet pet, PetEventConfig config) {
        PetEventType type;
        try {
            type = PetEventType.valueOf(config.getEventType());
        } catch (IllegalArgumentException e) {
            log.warn("未知活动统计口径: code={}, type={}", config.getCode(), config.getEventType());
            return 0;
        }
        return switch (type) {
            // B16：BOTTLE 默认只统计 CAUGHT（远程失败/空手不计入成功）；如运营要统计参与次数，新增明确事件类型
            case BOTTLE -> toInt(bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                    .eq(PetBottleRecord::getPetId, pet.getId())
                    .eq(PetBottleRecord::getOutcome, "CAUGHT")));
            case BATTLE -> toInt(battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                    .eq(PetBattle::getWinnerPetId, pet.getId())
                    .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name())));
            case WORK, STUDY, FEED, PLAY, VISIT ->
                    toInt(activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                            .eq(PetActivity::getPetId, pet.getId())
                            .eq(PetActivity::getActivityType, type.name())
                            .in(PetActivity::getStatus, Set.of(
                                    PetActivityStatus.CLAIMED.name(), PetActivityStatus.COMPLETED.name()))));
        };
    }

    private int toInt(Long value) {
        return value != null ? value.intValue() : 0;
    }

    /** B16：窗口判定按显式模式——LIFETIME 无时间窗；WINDOW 按 [startsAt, endsAt) */
    private boolean inWindow(PetEventConfig config, LocalDateTime now) {
        if ("WINDOW".equals(config.getEventMode())) {
            if (config.getStartsAt() != null && now.isBefore(config.getStartsAt())) {
                return false;
            }
            return config.getEndsAt() == null || now.isBefore(config.getEndsAt());
        }
        return true;
    }

    /** B16：活动结束后允许领取已达成奖励的时长（默认 24h，创建时快照固定） */
    static final long CLAIM_GRACE_HOURS = 24;

    /** 领奖资格：已开始、未过领取截止（WINDOW 模式） */
    private void requireClaimWindow(PetEventConfig config, LocalDateTime now) {
        if (!"WINDOW".equals(config.getEventMode())) {
            return;
        }
        if (config.getStartsAt() != null && now.isBefore(config.getStartsAt())) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FINISHED, "活动还没有开始哦");
        }
        if (config.getEndsAt() != null
                && now.isAfter(config.getEndsAt().plusHours(CLAIM_GRACE_HOURS))) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_ENDED, "活动奖励领取已截止，下次早点来～");
        }
    }

    private int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
