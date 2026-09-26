package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetDailyQuest;
import com.cloudmart.pet.entity.PetDailyQuestConfig;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestStatus;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetDailyQuestConfigMapper;
import com.cloudmart.pet.repository.PetDailyQuestMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetDailyQuestItemVO;
import com.cloudmart.pet.vo.PetDailyQuestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 每日任务实现（三期）。
 *
 * <p>三条不变量：
 * <ol>
 *   <li><b>当日任务行惰性生成</b>：{@code uk_pet_daily_quest} 幂等，先查后插 + 唯一索引兜底并发，
 *       目标值在生成时快照（后台改配置不影响当天已生成任务）。</li>
 *   <li><b>进度累加原子</b>：{@code UPDATE ... SET progress = LEAST(progress + n, target_value)}
 *       一条 SQL 完成，天然避免"读-改-写"丢更新；状态用 CASE 同步翻转（MySQL 左到右求值）。</li>
 *   <li><b>领奖幂等</b>：CAS（COMPLETE → CLAIMED）影响行数 0 即重复领取；
 *       经验本地 + 星光 Feign 同事务（失败整体回滚，AGENTS §17）。</li>
 * </ol>
 * 宝箱用保留任务码 {@code DAILY_CHEST} 记录在同一张表，复用上面的 CAS 幂等机制。</p>
 */
@Service
@Slf4j
public class PetDailyQuestServiceImpl implements PetDailyQuestService {

    /** 全清宝箱的保留任务码（不对应配置表，只在 pet_daily_quest 中留一行审计） */
    static final String CHEST_CODE = "DAILY_CHEST";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetDailyQuestConfigMapper configMapper;
    private final PetDailyQuestMapper questMapper;
    private final WishFeignClient wishFeignClient;
    private final PetOperationService operationService;
    private final PetClock petClock;
    private final PetIntimacyService intimacyService;
    private final PetAchievementService achievementService;
    private final PetProperties properties;

    public PetDailyQuestServiceImpl(PetService petService,
                                    PetStateService stateService,
                                    PetDailyQuestConfigMapper configMapper,
                                    PetDailyQuestMapper questMapper,
                                    WishFeignClient wishFeignClient,
                                    PetOperationService operationService,
                                    PetClock petClock,
                                    PetIntimacyService intimacyService,
                                    PetAchievementService achievementService,
                                    PetProperties properties) {
        this.petService = petService;
        this.stateService = stateService;
        this.configMapper = configMapper;
        this.questMapper = questMapper;
        this.wishFeignClient = wishFeignClient;
        this.operationService = operationService;
        this.petClock = petClock;
        this.intimacyService = intimacyService;
        this.achievementService = achievementService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public PetDailyQuestVO list(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return buildVo(pet, ensureToday(pet));
    }

    @Override
    @Transactional
    public PetDailyQuestItemVO claim(Long userId, String questCode) {
        if (questCode == null || questCode.isBlank() || CHEST_CODE.equals(questCode)) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务不存在");
        }
        Pet pet = petService.requireOwnedPet(userId);
        ensureToday(pet);
        PetDailyQuest quest = requireQuest(pet, questCode);
        if (!PetQuestStatus.COMPLETE.name().equals(quest.getStatus())) {
            if (PetQuestStatus.CLAIMED.name().equals(quest.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_QUEST_ALREADY_CLAIMED, "这个任务奖励已经领过啦");
            }
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FINISHED, "任务还没完成，再努努力吧");
        }
        int claimed = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.CLAIMED.name())
                .set(PetDailyQuest::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, quest.getId())
                .eq(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_ALREADY_CLAIMED, "这个任务奖励已经领过啦");
        }
        PetDailyQuestConfig config = configByCode(questCode);
        int expReward = config != null ? orZero(config.getExpReward()) : 0;
        int currencyReward = config != null ? orZero(config.getCurrencyReward()) : 0;
        // 亲密度在写库前先叠加（与经验同一次乐观锁写入）
        intimacyService.gain(pet, PetIntimacySource.QUEST);
        int levelups = stateService.grantExp(pet, expReward);
        if (currencyReward > 0) {
            // B01：本地奖励已生效；星光结果未知不回滚，恢复任务按原单收敛
            String operationId = operationService.operationKey("QUEST_CLAIM", quest.getId());
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, userId, pet.getId(), "QUEST_CLAIM", quest.getId(), currencyReward, null);
            if (!settlement.isCompleted()) {
                log.info("任务奖励星光结算中, questId={}, operationId={}", quest.getId(), operationId);
            }
        }
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        }
        achievementService.evaluate(pet, PetAchievementService.Event.QUEST);
        quest.setStatus(PetQuestStatus.CLAIMED.name());
        return toItemVo(quest, config);
    }

    @Override
    @Transactional
    public PetDailyQuestVO claimChest(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetDailyQuest> quests = ensureToday(pet);
        PetProperties.DailyQuest cfg = properties.getDailyQuest();
        List<PetDailyQuest> normalQuests = quests.stream()
                .filter(q -> !CHEST_CODE.equals(q.getQuestCode()))
                .toList();
        PetDailyQuest chest = quests.stream()
                .filter(q -> CHEST_CODE.equals(q.getQuestCode()))
                .findFirst()
                .orElse(null);
        if (chest == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "今日宝箱还没准备好");
        }
        if (PetQuestStatus.CLAIMED.name().equals(chest.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        boolean chestReady = !normalQuests.isEmpty()
                && normalQuests.stream()
                .filter(q -> !PetQuestStatus.CANCELLED.name().equals(q.getStatus()))
                .allMatch(q -> PetQuestStatus.CLAIMED.name().equals(q.getStatus()));
        if (!chestReady) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_NOT_READY, "把今天的任务都领完才能开宝箱哦");
        }
        // 宝箱可领：先用 CAS 抢占（COMPLETE → CLAIMED），再发奖（与任务领奖同一幂等口径）
        int marked = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name())
                .set(PetDailyQuest::getProgress, normalQuests.size())
                .set(PetDailyQuest::getTargetValue, normalQuests.size())
                .set(PetDailyQuest::getCompletedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, chest.getId())
                .in(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
        if (marked == 0 && !PetQuestStatus.COMPLETE.name().equals(chest.getStatus())) {
            // 并发下另一个请求已领取
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        int claimed = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.CLAIMED.name())
                .set(PetDailyQuest::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, chest.getId())
                .eq(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        int levelups = stateService.grantExp(pet, cfg.getChestExp());
        if (cfg.getChestCurrency() > 0) {
            String operationId = operationService.operationKey("QUEST_CHEST", userId, chest.getId());
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, userId, pet.getId(), "QUEST_CHEST", chest.getId(), cfg.getChestCurrency(), null);
            if (!settlement.isCompleted()) {
                log.info("宝箱奖励星光结算中, chestId={}, operationId={}", chest.getId(), operationId);
            }
        }
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        }
        List<PetDailyQuest> refreshed = questMapper.selectList(todayWrapper(pet));
        return buildVo(pet, refreshed);
    }

    @Override
    public void record(Pet pet, PetQuestType type, int amount) {
        if (pet == null || type == null || amount <= 0) {
            return;
        }
        try {
            ensureToday(pet);
            List<PetDailyQuestConfig> configs = activeConfigs(pet);
            List<String> codes = configs.stream()
                    .filter(c -> type.name().equals(c.getQuestType()))
                    .map(PetDailyQuestConfig::getCode)
                    .toList();
            if (codes.isEmpty()) {
                return;
            }
            for (String code : codes) {
                // 原子累加 + 状态翻转（progress 先赋值，后续 CASE 读到的是新值）
                questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                        .setSql("progress = LEAST(progress + " + amount + ", target_value)")
                        .setSql("status = CASE WHEN status = 'IN_PROGRESS' AND progress >= target_value "
                                + "THEN 'COMPLETE' ELSE status END")
                        .setSql("completed_at = CASE WHEN completed_at IS NULL AND progress >= target_value "
                                + "THEN UTC_TIMESTAMP() ELSE completed_at END")
                        .eq(PetDailyQuest::getPetId, pet.getId())
                        .eq(PetDailyQuest::getQuestDate, LocalDate.now(ZoneId.of("UTC")))
                        .eq(PetDailyQuest::getQuestCode, code)
                        .eq(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
            }
        } catch (Exception e) {
            // 埋点失败不影响主玩法（进度可丢，玩法不可断）
            log.warn("每日任务埋点失败（忽略）: petId={}, type={}, amount={}", pet.getId(), type, amount, e);
        }
    }

    // ---------------- 内部 ----------------

    /** 生成/加载当日任务行（含宝箱行），返回当日全部行 */
    private List<PetDailyQuest> ensureToday(Pet pet) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<PetDailyQuest> existing = questMapper.selectList(todayWrapper(pet));
        List<String> existingCodes = existing.stream().map(PetDailyQuest::getQuestCode).toList();
        List<PetDailyQuestConfig> configs = frozenConfigsForToday(pet, today);
        for (PetDailyQuestConfig config : configs) {
            if (existingCodes.contains(config.getCode())) {
                continue;
            }
            PetDailyQuest quest = new PetDailyQuest();
            quest.setPetId(pet.getId());
            quest.setUserId(pet.getUserId());
            quest.setQuestDate(today);
            quest.setQuestCode(config.getCode());
            quest.setProgress(0);
            quest.setTargetValue(config.getTargetValue());
            quest.setStatus(PetQuestStatus.IN_PROGRESS.name());
            try {
                questMapper.insert(quest);
                existing.add(quest);
            } catch (DuplicateKeyException e) {
                // 并发生成：uk_pet_daily_quest 兜底，重读即可
                log.debug("每日任务并发生成，忽略: petId={}, code={}", pet.getId(), config.getCode());
            }
        }
        if (existing.stream().noneMatch(q -> CHEST_CODE.equals(q.getQuestCode()))) {
            PetDailyQuest chest = new PetDailyQuest();
            chest.setPetId(pet.getId());
            chest.setUserId(pet.getUserId());
            chest.setQuestDate(today);
            chest.setQuestCode(CHEST_CODE);
            chest.setProgress(0);
            chest.setTargetValue(configs.size());
            chest.setStatus(PetQuestStatus.IN_PROGRESS.name());
            try {
                questMapper.insert(chest);
                existing.add(chest);
            } catch (DuplicateKeyException e) {
                log.debug("每日宝箱并发生成，忽略: petId={}", pet.getId());
            }
        }
        return questMapper.selectList(todayWrapper(pet));
    }

    private LambdaQueryWrapper<PetDailyQuest> todayWrapper(Pet pet) {
        return new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, pet.getId())
                .eq(PetDailyQuest::getQuestDate, LocalDate.now(ZoneId.of("UTC")))
                .orderByAsc(PetDailyQuest::getId);
    }

    /** 当日启用的任务配置（按宠物等级过滤） */
    private List<PetDailyQuestConfig> activeConfigs(Pet pet) {
        return configMapper.selectList(new LambdaQueryWrapper<PetDailyQuestConfig>()
                        .eq(PetDailyQuestConfig::getEnabled, true)
                        .orderByAsc(PetDailyQuestConfig::getSort))
                .stream()
                .filter(c -> pet.getLevel() >= (c.getRequiredLevel() != null ? c.getRequiredLevel() : 1))
                .toList();
    }

    /**
     * B15：生成当日任务只使用"本业务日开始前已存在"的配置——当日新增/升级解锁的配置
     * 次日生效，冻结任务集合；targetValue 在创建时快照。
     */
    private List<PetDailyQuestConfig> frozenConfigsForToday(Pet pet, LocalDate today) {
        LocalDateTime dayStartUtc = petClock.businessDateStartUtc(today);
        return activeConfigs(pet).stream()
                .filter(c -> c.getCreatedAt() == null || c.getCreatedAt().isBefore(dayStartUtc))
                .toList();
    }

    /** B15：配置停用 → 当日快照显式置 CANCELLED（不静默消失，且不阻挡宝箱） */
    private int cancelDisabledQuests(List<PetDailyQuest> quests, java.util.Set<String> enabledCodes) {
        int cancelled = 0;
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())
                    || PetQuestStatus.IN_PROGRESS.name().equals(quest.getStatus())
                    && !enabledCodes.contains(quest.getQuestCode())) {
                if (!CHEST_CODE.equals(quest.getQuestCode())) {
                    cancelled += questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                            .set(PetDailyQuest::getStatus, PetQuestStatus.CANCELLED.name())
                            .eq(PetDailyQuest::getId, quest.getId())
                            .eq(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
                }
            }
        }
        return cancelled;
    }

    private PetDailyQuest requireQuest(Pet pet, String questCode) {
        PetDailyQuest quest = questMapper.selectOne(new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, pet.getId())
                .eq(PetDailyQuest::getQuestDate, LocalDate.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getQuestCode, questCode)
                .last("LIMIT 1"));
        if (quest == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务今天不在列表里");
        }
        return quest;
    }

    private PetDailyQuestConfig configByCode(String code) {
        return configMapper.selectOne(new LambdaQueryWrapper<PetDailyQuestConfig>()
                .eq(PetDailyQuestConfig::getCode, code)
                .last("LIMIT 1"));
    }

    private PetDailyQuestVO buildVo(Pet pet, List<PetDailyQuest> quests) {
        Map<String, PetDailyQuestConfig> configMap = configMapper.selectList(
                        new LambdaQueryWrapper<PetDailyQuestConfig>().eq(PetDailyQuestConfig::getEnabled, true))
                .stream()
                .collect(Collectors.toMap(PetDailyQuestConfig::getCode, Function.identity(), (a, b) -> a));
        PetProperties.DailyQuest chestCfg = properties.getDailyQuest();
        List<PetDailyQuestItemVO> items = new ArrayList<>();
        int completed = 0;
        int claimed = 0;
        PetDailyQuest chest = null;
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())) {
                chest = quest;
                continue;
            }
            PetDailyQuestConfig config = configMap.get(quest.getQuestCode());
            if (config == null || PetQuestStatus.CANCELLED.name().equals(quest.getStatus())) {
                // B15：配置已下架/任务被取消：显式展示 CANCELLED（不静默消失），不计入宝箱门禁
                items.add(new PetDailyQuestItemVO(quest.getQuestCode(),
                        config != null ? config.getName() : quest.getQuestCode(),
                        config != null ? config.getDescription() : "",
                        config != null ? config.getIcon() : "📌",
                        config != null ? config.getQuestType() : null,
                        quest.getProgress(), quest.getTargetValue(), quest.getStatus(),
                        "已取消", false,
                        config != null ? orZero(config.getExpReward()) : 0,
                        config != null ? orZero(config.getCurrencyReward()) : 0));
                continue;
            }
            if (!PetQuestStatus.IN_PROGRESS.name().equals(quest.getStatus())) {
                completed++;
            }
            if (PetQuestStatus.CLAIMED.name().equals(quest.getStatus())) {
                claimed++;
            }
            items.add(toItemVo(quest, config));
        }
        boolean allClaimed = !items.isEmpty() && claimed >= items.size();
        boolean chestClaimed = chest != null && PetQuestStatus.CLAIMED.name().equals(chest.getStatus());
        // 宝箱进度实时对齐（列表是只读入口，不做状态翻转，只展示真实进度）
        if (chest != null && !chestClaimed) {
            chest.setProgress(claimed);
        }
        return new PetDailyQuestVO(LocalDate.now(ZoneId.of("UTC")), items, completed, claimed, items.size(),
                allClaimed && !chestClaimed, chestClaimed,
                chestCfg.getChestExp(), chestCfg.getChestCurrency());
    }

    private PetDailyQuestItemVO toItemVo(PetDailyQuest quest, PetDailyQuestConfig config) {
        String status = quest.getStatus();
        String label = switch (status) {
            case "COMPLETE" -> "可领取";
            case "CLAIMED" -> "已领取";
            default -> "进行中";
        };
        return new PetDailyQuestItemVO(
                quest.getQuestCode(),
                config != null ? config.getName() : quest.getQuestCode(),
                config != null ? config.getDescription() : "",
                config != null ? config.getIcon() : "📌",
                config != null ? config.getQuestType() : null,
                quest.getProgress(),
                quest.getTargetValue(),
                status, label,
                PetQuestStatus.COMPLETE.name().equals(status),
                config != null ? orZero(config.getExpReward()) : 0,
                config != null ? orZero(config.getCurrencyReward()) : 0);
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
