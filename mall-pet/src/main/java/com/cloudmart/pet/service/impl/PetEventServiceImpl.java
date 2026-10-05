package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetEventConfig;
import com.cloudmart.pet.entity.PetEventOccurrence;
import com.cloudmart.pet.entity.PetEventOccurrenceClaim;
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
import com.cloudmart.pet.repository.PetEventOccurrenceClaimMapper;
import com.cloudmart.pet.repository.PetEventOccurrenceMapper;
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
    /** R33：期次（occurrence）驱动领奖 */
    private final PetEventOccurrenceMapper occurrenceMapper;
    private final PetEventOccurrenceClaimMapper occurrenceClaimMapper;
    private final PetActivityMapper activityMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetBattleMapper battleMapper;
    private final PetInventoryMapper inventoryMapper;
    private final WishFeignClient wishFeignClient;
    private final PetEconomyService economyService;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;

    public PetEventServiceImpl(PetStateService stateService,
                               PetEventConfigMapper eventConfigMapper,
                               PetEventProgressMapper progressMapper,
                               PetEventOccurrenceMapper occurrenceMapper,
                               PetEventOccurrenceClaimMapper occurrenceClaimMapper,
                               PetActivityMapper activityMapper,
                               PetBottleRecordMapper bottleRecordMapper,
                               PetBattleMapper battleMapper,
                               PetInventoryMapper inventoryMapper,
                               WishFeignClient wishFeignClient,
                               PetEconomyService economyService,
                               PetAchievementService achievementService,
                               PetEventProducer eventProducer) {
        this.stateService = stateService;
        this.eventConfigMapper = eventConfigMapper;
        this.progressMapper = progressMapper;
        this.occurrenceMapper = occurrenceMapper;
        this.occurrenceClaimMapper = occurrenceClaimMapper;
        this.activityMapper = activityMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.battleMapper = battleMapper;
        this.inventoryMapper = inventoryMapper;
        this.wishFeignClient = wishFeignClient;
        this.economyService = economyService;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
    }

    @Override
    public List<PetEventVO> events(Long userId) {
        return eventsForPet(stateService.requireActivePet(userId));
    }

    @Override
    public List<PetEventVO> events(Long userId, String status) {
        List<PetEventVO> all = events(userId);
        if (status == null || status.isBlank()) {
            return all;
        }
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> all.stream().filter(e -> !Boolean.TRUE.equals(e.completed())
                    && !Boolean.TRUE.equals(e.claimed()) && !Boolean.TRUE.equals(e.expired())).toList();
            case "CLAIMABLE" -> all.stream().filter(e -> Boolean.TRUE.equals(e.claimable())).toList();
            case "HISTORY" -> all.stream().filter(e -> Boolean.TRUE.equals(e.claimed())
                    || Boolean.TRUE.equals(e.expired())).toList();
            default -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "status 仅支持 AVAILABLE/CLAIMABLE/HISTORY");
        };
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
                // R33：宽限期内（endsAt ~ endsAt+24h）仍展示"已结束，可领奖"——
                // 原实现 endsAt 一到活动即从列表消失，已达成未领的奖励失去入口
                .filter(config -> inWindow(config, now) || inClaimGrace(config, now))
                .map(config -> {
                    // R33：期次驱动的活动以期次窗口/领奖截止为准（occurrenceId 返回给三端）
                    PetEventOccurrence occurrence = occurrenceMapper.selectOne(
                            new LambdaQueryWrapper<PetEventOccurrence>()
                                    .eq(PetEventOccurrence::getEventCode, config.getCode())
                                    .eq(PetEventOccurrence::getStatus, "ACTIVE")
                                    .ge(PetEventOccurrence::getClaimDeadlineAt, now)
                                    .orderByDesc(PetEventOccurrence::getOccurrenceIndex)
                                    .last("LIMIT 1"));
                    if (occurrence == null) {
                        return toVo(pet, config, now);
                    }
                    LocalDateTime countEnd = occurrence.getCountingStoppedAt() != null
                            && occurrence.getCountingStoppedAt().isBefore(occurrence.getEndAt())
                            ? occurrence.getCountingStoppedAt() : occurrence.getEndAt();
                    OccurrenceRewardSnapshot snapshot = snapshotOf(occurrence, config);
                    int progress = countProgressBetween(pet, resolveEventType(snapshot.eventType()),
                            occurrence.getStartAt(), countEnd);
                    return buildOccurrenceVo(config, occurrence, snapshot, pet.getId(), now, progress, null);
                })
                .toList();
    }

    /** R33：宽限期（WINDOW 结束后 24h 内）——已达成未领的奖励在此窗口内仍可领 */
    private boolean inClaimGrace(PetEventConfig config, LocalDateTime now) {
        if (!"WINDOW".equals(config.getEventMode()) || config.getEndsAt() == null) {
            return false;
        }
        return now.isAfter(config.getEndsAt())
                && !now.isAfter(config.getEndsAt().plusHours(CLAIM_GRACE_HOURS));
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
        // R33：旧 eventCode 入口仅当能唯一解析当前一期时使用——多期并存歧义明确拒绝
        List<PetEventOccurrence> current = occurrenceMapper.selectList(new LambdaQueryWrapper<PetEventOccurrence>()
                .eq(PetEventOccurrence::getEventCode, config.getCode())
                .eq(PetEventOccurrence::getStatus, "ACTIVE")
                .ge(PetEventOccurrence::getClaimDeadlineAt, now));
        if (current.size() > 1) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND,
                    "该活动存在多期可领记录，请按期次入口领取");
        }
        if (current.size() == 1) {
            return claimByOccurrence(userId, current.get(0).getId());
        }
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
                eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                        "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel(),
                        String.valueOf(userId), "PET_LEVEL_UP",
                        "宠物升级啦！",
                        pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                        String.valueOf(pet.getId()), "PET_LEVEL_UP"), pet.getId());
            }
        }
        int starlight = orZero(config.getRewardStarlight());
        if (starlight > 0) {
            // 操作键绑定 (pet, event, claimDate)：同一活动多次领取只一次收益
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    userId, pet.getId(), "EVENT_CLAIM", pet.getId(), starlight, null,
                    pet.getId(), config.getCode(), now.toLocalDate());
            if (!settlement.isCompleted()) {
                log.info("活动奖励星光结算中, eventCode={}, status={}", config.getCode(), settlement.status());
            }
        }
        grantRewardWithAlternative(pet, config.getRewardItemCode(), orZero(config.getRewardAltStarlight()), now,
                pet.getId(), config.getCode(), now.toLocalDate());
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
    /**
     * B16/PET-11：唯一物品发放——已拥有时按快照发固定替代星光（走 B01 幂等操作），
     * 无替代或替代为 0 时明确跳过且不重复入包。幂等键由调用方给齐：
     * 期次路径含 occurrenceId（同日多期独立），旧 eventCode 路径按领取日收敛。
     */
    private void grantRewardWithAlternative(Pet pet, String itemCode, int altStarlight,
                                            LocalDateTime now, Object... keyParts) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        boolean owned = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.EQUIPMENT.name())
                .eq(PetInventory::getItemCode, itemCode)) > 0;
        if (!owned) {
            boolean inserted = grantRewardItem(pet, itemCode, now);
            if (inserted) {
                return;
            }
            // R33：插入撞唯一键（并发同来源已发同一物品）→ 转替代星光一次，
            // 不再只记日志把替代奖吞掉（T60：一份 ITEM 或一次 ALTERNATIVE，结果可解释）
        }
        if (altStarlight <= 0) {
            log.info("活动奖励物品已拥有且无替代星光, petId={}, item={}", pet.getId(), itemCode);
            return;
        }
        PetEconomyService.WalletSettlement settlement = economyService.earn(
                pet.getUserId(), pet.getId(), "EVENT_ALT", pet.getId(), altStarlight, null, keyParts);
        if (!settlement.isCompleted()) {
            log.info("活动替代星光结算中, status={}", settlement.status());
        }
    }

    /** @return true=物品已入包；false=撞唯一键（并发重复，调用方转替代奖励） */
    private boolean grantRewardItem(Pet pet, String itemCode, LocalDateTime now) {
        if (itemCode == null || itemCode.isBlank()) {
            return true;
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
            return true;
        } catch (DuplicateKeyException e) {
            // R33：并发同来源已发同一物品 → 调用方转替代星光（不再只记日志吞掉替代奖）
            log.debug("活动奖励物品插入撞唯一键，转替代奖励: petId={}, item={}", pet.getId(), itemCode);
            return false;
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
    /**
     * R33 §7.2/PET-11：按期次领取。唯一领奖事实 uk(occurrence_id, pet_id)——同宠同期至多一次；
     * 期限：now ≤ claimDeadlineAt（结束+宽限）；进度按期次窗口 [startAt, endAt) 统计；
     * 目标/奖励/替代奖励一律按发布时冻结的期次快照发放（模板后续编辑不改变当期，T23），
     * 旧期次无快照回退当前配置并按 legacy 处理；发奖幂等键含 occurrenceId（同日多期不合并，T24）。
     */
    @Override
    @Transactional
    public PetEventVO claimByOccurrence(Long userId, Long occurrenceId) {
        Pet pet = stateService.requireActivePet(userId);
        PetEventOccurrence occurrence = occurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "活动期次不存在");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        // V66：停止领奖与截止双闸——运营 stop-claim 到点即拒
        if (occurrence.getClaimStoppedAt() != null && now.isAfter(occurrence.getClaimStoppedAt())) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "本期领奖已停止");
        }
        if (now.isAfter(occurrence.getClaimDeadlineAt())) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "本期领奖已截止");
        }
        PetEventConfig config = eventConfigMapper.selectOne(new LambdaQueryWrapper<PetEventConfig>()
                .eq(PetEventConfig::getCode, occurrence.getEventCode())
                .eq(PetEventConfig::getEnabled, true)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "活动不存在或已下架");
        }
        OccurrenceRewardSnapshot snapshot = snapshotOf(occurrence, config);
        // V66：计数停止后窗口按停止时刻截断（停止前的事实仍计入，不追溯清零）
        LocalDateTime countEnd = occurrence.getCountingStoppedAt() != null
                && occurrence.getCountingStoppedAt().isBefore(occurrence.getEndAt())
                ? occurrence.getCountingStoppedAt() : occurrence.getEndAt();
        int progress = countProgressBetween(pet, resolveEventType(snapshot.eventType()),
                occurrence.getStartAt(), countEnd);
        int target = snapshot.targetValue() > 0 ? snapshot.targetValue() : 1;
        if (progress < target) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FINISHED,
                    "还差 " + (target - progress) + " 次就能完成啦");
        }
        // 唯一领奖事实：同宠同期重复领取撞 uk 明确拒绝
        PetEventOccurrenceClaim fact = new PetEventOccurrenceClaim();
        fact.setOccurrenceId(occurrence.getId());
        fact.setEventCode(occurrence.getEventCode());
        fact.setPetId(pet.getId());
        fact.setUserId(userId);
        fact.setRewardSnapshot(occurrence.getRewardSnapshot());
        try {
            occurrenceClaimMapper.insert(fact);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_ALREADY_CLAIMED, "本期奖励已经领取过啦");
        }

        if (snapshot.rewardExp() > 0) {
            int levelups = stateService.grantExp(pet, snapshot.rewardExp());
            if (levelups > 0) {
                achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
                eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                        "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel(),
                        String.valueOf(userId), "PET_LEVEL_UP",
                        "宠物升级啦！",
                        pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                        String.valueOf(pet.getId()), "PET_LEVEL_UP"), pet.getId());
            }
        }
        if (snapshot.rewardStarlight() > 0) {
            // PET-11 发奖键含 occurrenceId：同日多期各自独立入账，替代奖励不再跨期合并
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    userId, pet.getId(), "EVENT_CLAIM", pet.getId(), snapshot.rewardStarlight(), null,
                    pet.getId(), config.getCode(), occurrence.getId());
            if (!settlement.isCompleted()) {
                log.info("活动奖励星光结算中, eventCode={}, occurrence={}, status={}",
                        config.getCode(), occurrence.getOccurrenceIndex(), settlement.status());
            }
        }
        grantRewardWithAlternative(pet, snapshot.rewardItemCode(), snapshot.rewardAltStarlight(), now,
                pet.getId(), config.getCode(), occurrence.getId());
        return buildOccurrenceVo(config, occurrence, snapshot, pet.getId(), now, progress, now);
    }

    /**
     * PET-11：期次奖励快照解析。缺字段逐项回退当前配置；整份缺失/解析失败标记 legacy
     * （历史期次先按配置口径处理，不做静默套未来配置）。
     */
    private OccurrenceRewardSnapshot snapshotOf(PetEventOccurrence occurrence, PetEventConfig config) {
        String raw = occurrence.getRewardSnapshot();
        if (raw == null || raw.isBlank()) {
            return legacySnapshotOf(occurrence, config);
        }
        java.util.Map<String, Object> snapshot;
        try {
            snapshot = com.cloudmart.pet.util.PetJsonUtils.parse(raw,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.warn("期次奖励快照解析失败，回退当前配置: occurrenceId={}, eventCode={}",
                    occurrence.getId(), occurrence.getEventCode());
            return legacySnapshotOf(occurrence, config);
        }
        return new OccurrenceRewardSnapshot(
                intValueOf(snapshot.get("targetValue"), config.getTargetValue()),
                intValueOf(snapshot.get("rewardStarlight"), config.getRewardStarlight()),
                intValueOf(snapshot.get("rewardExp"), config.getRewardExp()),
                stringValueOf(snapshot.get("rewardItemCode"), orEmpty(config.getRewardItemCode())),
                intValueOf(snapshot.get("rewardAltStarlight"), config.getRewardAltStarlight()),
                stringValueOf(snapshot.get("eventType"), config.getEventType()),
                false);
    }

    private OccurrenceRewardSnapshot legacySnapshotOf(PetEventOccurrence occurrence, PetEventConfig config) {
        log.warn("期次缺少奖励快照（legacy），按当前配置发放: occurrenceId={}, eventCode={}",
                occurrence.getId(), occurrence.getEventCode());
        return new OccurrenceRewardSnapshot(orZero(config.getTargetValue()), orZero(config.getRewardStarlight()),
                orZero(config.getRewardExp()), orEmpty(config.getRewardItemCode()),
                orZero(config.getRewardAltStarlight()), config.getEventType(), true);
    }

    /** 期次冻结奖励（PET-11）：claim 与 VO 展示共用同一口径 */
    record OccurrenceRewardSnapshot(int targetValue, int rewardStarlight, int rewardExp,
                                    String rewardItemCode, int rewardAltStarlight,
                                    String eventType, boolean legacy) {
    }

    private static int intValueOf(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback != null ? fallback : 0;
    }

    private static String stringValueOf(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    /** 期次视角 VO：目标与奖励展示取冻结快照（PET-11），claimed 取本期领奖事实；claimable 按 claimDeadline 判定 */
    private PetEventVO buildOccurrenceVo(PetEventConfig config, PetEventOccurrence occurrence,
                                         OccurrenceRewardSnapshot snapshot, Long petId,
                                         LocalDateTime now, int progress, LocalDateTime claimedAt) {
        int target = snapshot.targetValue() > 0 ? snapshot.targetValue() : 1;
        Long claimedRows = occurrenceClaimMapper.selectCount(new LambdaQueryWrapper<PetEventOccurrenceClaim>()
                .eq(PetEventOccurrenceClaim::getOccurrenceId, occurrence.getId())
                .eq(PetEventOccurrenceClaim::getPetId, petId));
        boolean claimed = claimedRows != null && claimedRows > 0;
        boolean completed = progress >= target;
        boolean claimStopped = occurrence.getClaimStoppedAt() != null
                && now.isAfter(occurrence.getClaimStoppedAt());
        boolean claimable = completed && !claimed && !claimStopped
                && !now.isAfter(occurrence.getClaimDeadlineAt());
        boolean expired = !completed && now.isAfter(occurrence.getEndAt());
        return new PetEventVO(config.getCode(), config.getName(), config.getDescription(),
                config.getEventType(), target, progress, completed, claimable, claimed, expired,
                snapshot.rewardStarlight(), snapshot.rewardExp(),
                snapshot.rewardItemCode().isEmpty() ? null : snapshot.rewardItemCode(),
                occurrence.getStartAt(), occurrence.getEndAt(), claimedAt,
                String.valueOf(occurrence.getId()), occurrence.getClaimDeadlineAt());
    }

    private PetEventVO buildVo(PetEventConfig config, LocalDateTime now, int progress, LocalDateTime claimedAt) {
        int target = config.getTargetValue() != null ? config.getTargetValue() : 1;
        boolean claimed = claimedAt != null;
        boolean completed = progress >= target;
        // R33：claimable 与实际领取调用同一资格函数（宽限外不可领，列表按钮不误导）
        boolean graceOver = "WINDOW".equals(config.getEventMode()) && config.getEndsAt() != null
                && now.isAfter(config.getEndsAt().plusHours(CLAIM_GRACE_HOURS));
        boolean claimable = completed && !claimed && !graceOver;
        boolean expired = !completed && config.getEndsAt() != null && now.isAfter(config.getEndsAt());
        return new PetEventVO(config.getCode(), config.getName(), config.getDescription(),
                config.getEventType(), target, progress, completed, claimable, claimed, expired,
                config.getRewardStarlight(), config.getRewardExp(), config.getRewardItemCode(),
                config.getStartsAt(), config.getEndsAt(), claimedAt, null, null);
    }

    /** 进度统计（惰性，不落计数器）：按统计口径 COUNT 既有业务表 */
    /** 统计口径解析（未知类型返回 null，由 countProgressBetween 归 0） */
    private PetEventType resolveEventType(PetEventConfig config) {
        return resolveEventType(config.getCode(), config.getEventType());
    }

    /** 快照事件类型解析（PET-11：期次进度按发布时冻结的 eventType 投影） */
    private PetEventType resolveEventType(String eventType) {
        return resolveEventType(null, eventType);
    }

    private PetEventType resolveEventType(String eventCode, String eventType) {
        try {
            return PetEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.warn("未知活动统计口径: code={}, type={}", eventCode, eventType);
            return null;
        }
    }

    int countProgress(Pet pet, PetEventConfig config) {
        PetEventType type;
        try {
            type = PetEventType.valueOf(config.getEventType());
        } catch (IllegalArgumentException e) {
            log.warn("未知活动统计口径: code={}, type={}", config.getCode(), config.getEventType());
            return 0;
        }
        // R33：WINDOW 模式按 [startsAt, endsAt) 统计窗口内事实（原实现统计历史全量，
        // 活动开始前的成绩也计入限时目标）；LIFETIME 才统计累积总量。
        boolean windowed = "WINDOW".equals(config.getEventMode());
        LocalDateTime from = windowed ? config.getStartsAt() : null;
        LocalDateTime to = windowed ? config.getEndsAt() : null;
        return countProgressBetween(pet, type, from, to);
    }

    /** R33：按显式窗口统计（occurrence 期次用自身窗口覆盖配置窗口） */
    int countProgressBetween(Pet pet, PetEventType type, LocalDateTime from, LocalDateTime to) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            // B16：BOTTLE 默认只统计 CAUGHT（远程失败/空手不计入成功）；如运营要统计参与次数，新增明确事件类型
            case BOTTLE -> toInt(bottleRecordMapper.selectCount(new LambdaQueryWrapper<PetBottleRecord>()
                    .eq(PetBottleRecord::getPetId, pet.getId())
                    .eq(PetBottleRecord::getOutcome, "CAUGHT")
                    .ge(from != null, PetBottleRecord::getFinishedAt, from)
                    .lt(to != null, PetBottleRecord::getFinishedAt, to)));
            case BATTLE -> toInt(battleMapper.selectCount(new LambdaQueryWrapper<PetBattle>()
                    .eq(PetBattle::getWinnerPetId, pet.getId())
                    .eq(PetBattle::getStatus, PetBattleStatus.FINISHED.name())
                    .ge(from != null, PetBattle::getFinishedAt, from)
                    .lt(to != null, PetBattle::getFinishedAt, to)));
            case WORK, STUDY, FEED, PLAY, VISIT ->
                    toInt(activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                            .eq(PetActivity::getPetId, pet.getId())
                            .eq(PetActivity::getActivityType, type.name())
                            .in(PetActivity::getStatus, Set.of(
                                    PetActivityStatus.CLAIMED.name(), PetActivityStatus.COMPLETED.name()))
                            .ge(from != null, PetActivity::getFinishedAt, from)
                            .lt(to != null, PetActivity::getFinishedAt, to)));
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

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
