package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ChallengeBattleRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.enums.PetBattleMode;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetBattleService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetBattleVO;
import com.cloudmart.pet.vo.PetOpponentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对战服务实现。
 *
 * <p>公平性与防作弊：挑战创建时快照双方属性 + 生成 seed 落库，防守方 accept 时
 * 按"创建时刻的快照"计算——中途养成/换装不影响已发起挑战；客户端伪造参数无入口
 * （mode/defenderPetId 之外无可控字段，奖励/胜负全部服务端计算）。</p>
 *
 * <p>奖励一致性：accept/decline 均 CAS 流转后同一事务内发奖，星光 Feign 失败
 * 抛 503 整体回滚可重试（与打工领取同语义）。</p>
 */
@Service
@Slf4j
public class PetBattleServiceImpl implements PetBattleService {

    private static final int OPPONENT_LIMIT = 8;
    private static final int WILD_OPPONENT_COUNT = 3;
    private static final String WILD_OWNER_PLACEHOLDER = "野生宠物";
    private static final String OWNER_PLACEHOLDER = "匿名训练家";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetBattleMapper battleMapper;
    private final PetMapper petMapper;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final PetStatsService statsService;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetRelationService relationService;
    private final PetOperationService operationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PetBattleServiceImpl(PetService petService,
                                PetStateService stateService,
                                PetBattleMapper battleMapper,
                                PetMapper petMapper,
                                WishFeignClient wishFeignClient,
                                PetAchievementService achievementService,
                                PetEventProducer eventProducer,
                                PetProperties properties,
                                PetStatsService statsService,
                                PetDailyQuestService dailyQuestService,
                                PetIntimacyService intimacyService,
                                PetRelationService relationService,
                                PetOperationService operationService,
                                com.cloudmart.pet.service.PetUserBlockService userBlockService) {
        this.petService = petService;
        this.stateService = stateService;
        this.battleMapper = battleMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.properties = properties;
        this.statsService = statsService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.relationService = relationService;
        this.operationService = operationService;
        this.userBlockService = userBlockService;
    }

    @Override
    public List<PetOpponentVO> listOpponents(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetOpponentVO> opponents = new ArrayList<>(wildOpponents(pet));

        // B22：随机偏移抽样替代 ORDER BY RAND()（可索引候选池，保持等级段与隐私过滤）
        List<Pet> rivals = sampleRivals(userId, pet);
        // 等级段对手不足时放宽等级补齐
        if (rivals.size() < OPPONENT_LIMIT) {
            List<Long> pickedIds = rivals.stream().map(Pet::getId).toList();
            List<Pet> fallback = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                    .ne(Pet::getUserId, userId)
                    .eq(Pet::getIsPublic, true)
                    .notIn(!pickedIds.isEmpty(), Pet::getId, pickedIds)
                    .last("ORDER BY RAND() LIMIT " + (OPPONENT_LIMIT - rivals.size())));
            rivals.addAll(fallback);
        }
        Map<Long, String> nicknames = resolveNicknames(rivals.stream().map(Pet::getUserId).toList());
        rivals.forEach(rival -> opponents.add(new PetOpponentVO(rival.getId(), rival.getName(),
                rival.getSpecies(), rival.getLevel(), rival.getGrowthStage(), false, rival.getUserId(),
                nicknames.getOrDefault(rival.getUserId(), OWNER_PLACEHOLDER), null)));
        return opponents;
    }

    @Override
    @Transactional
    public PetBattleVO challenge(Long userId, ChallengeBattleRequest request) {
        Pet attacker = petService.requireOwnedPet(userId);
        PetBattleMode mode = parseMode(request.mode());

        if (mode == PetBattleMode.PVE) {
            // B08：稳定野生模板——按请求 templateId 挑战（旧客户端缺省 1），与列表展示一致
            int templateId = request.templateId() != null ? request.templateId() : 1;
            if (templateId < 1 || templateId > WILD_TEMPLATE_NAMES.length) {
                throw new BusinessException(PetErrorCodes.PET_BATTLE_OPPONENT_INVALID, "野生对手不存在");
            }
            PetBattleEngine.Fighter wild = wildFighter(attacker.getLevel(), templateId);
            return settleNewBattle(attacker, wild, PetBattleMode.PVE, 0L, null);
        }
        // PvP：防守方必须存在、公开、非自己
        if (request.defenderPetId() == null) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_OPPONENT_INVALID, "请选择对战对手");
        }
        Pet defender = petMapper.selectById(request.defenderPetId());
        if (defender == null || !Boolean.TRUE.equals(defender.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_OPPONENT_INVALID, "对手宠物不存在或未公开");
        }
        if (defender.getUserId().equals(userId)) {
        if (userBlockService.isBlockedEitherWay(userId, defender.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法挑战该用户");
        }
            throw new BusinessException(PetErrorCodes.PET_BATTLE_SELF_CHALLENGE, "不能挑战自己的宠物哦");
        }

        long seed = secureRandom.nextLong();
        PetBattle battle = buildBattle(attacker, toFighter(defender), PetBattleMode.PVP,
                defender.getId(), defender.getUserId(), seed);
        battle.setStatus(PetBattleStatus.PENDING.name());
        battleMapper.insert(battle);

        eventProducer.publish(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                "BATTLE_CHALLENGE:" + battle.getId(),
                String.valueOf(defender.getUserId()), "PET_BATTLE_CHALLENGE",
                "有人向我发起挑战啦！",
                attacker.getName() + " 向 " + defender.getName() + " 发起了对战挑战，去应战吧！",
                String.valueOf(battle.getId()), "PET_BATTLE_CHALLENGE"));
        return toVo(battle, userId, null);
    }

    @Override
    @Transactional
    public PetBattleVO accept(Long userId, Long battleId) {
        PetBattle battle = requireBattle(battleId);
        if (!userId.equals(battle.getDefenderUserId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只有被挑战方可以应战");
        }
        int updated = battleMapper.update(null, new LambdaUpdateWrapper<PetBattle>()
                .set(PetBattle::getStatus, PetBattleStatus.FINISHED.name())
                .set(PetBattle::getFinishedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetBattle::getId, battleId)
                .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_ALREADY_HANDLED, "这场挑战已经被处理过啦");
        }

        PetBattleEngine.Fighter attacker = PetJsonUtils.parse(battle.getAttackerSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        PetBattleEngine.Fighter defender = PetJsonUtils.parse(battle.getDefenderSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(
                attacker, defender, battle.getSeed(), properties.getBattle().getMaxRounds());

        battle.setStatus(PetBattleStatus.FINISHED.name());
        battle.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setWinnerPetId(result.winnerPetId());
        battle.setRounds(PetJsonUtils.toJson(result.rounds()));
        int winExp = properties.getBattle().getWinExp();
        int loseExp = properties.getBattle().getLoseExp();
        int expReward = result.attackerWon() ? winExp : loseExp;
        battle.setExpReward(expReward);
        battle.setCurrencyReward(result.attackerWon() ? properties.getBattle().getWinCurrency() : 0);
        battleMapper.updateById(battle);

        grantRewards(attacker.petId(), defender.petId(), result.attackerWon(), battle);
        return toVo(battle, userId, result.rounds());
    }

    @Override
    @Transactional
    public PetBattleVO decline(Long userId, Long battleId) {
        PetBattle battle = requireBattle(battleId);
        if (!userId.equals(battle.getDefenderUserId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只有被挑战方可以拒绝");
        }
        int updated = battleMapper.update(null, new LambdaUpdateWrapper<PetBattle>()
                .set(PetBattle::getStatus, PetBattleStatus.DECLINED.name())
                .eq(PetBattle::getId, battleId)
                .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_ALREADY_HANDLED, "这场挑战已经被处理过啦");
        }
        battle.setStatus(PetBattleStatus.DECLINED.name());
        eventProducer.publish(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                "BATTLE_DECLINED:" + battle.getId(),
                String.valueOf(battle.getAttackerUserId()), "PET_BATTLE_FINISHED",
                "挑战被拒绝啦",
                "对方婉拒了这次对战，换一个对手试试吧！",
                String.valueOf(battle.getId()), "PET_BATTLE_DECLINED"));
        return toVo(battle, userId, null);
    }

    @Override
    public PetBattleVO get(Long userId, Long battleId) {
        PetBattle battle = requireBattle(battleId);
        boolean participant = userId.equals(battle.getAttackerUserId())
                || userId.equals(battle.getDefenderUserId());
        if (!participant) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "只能查看自己的对战记录");
        }
        return toVo(battle, userId, null);
    }

    @Override
    public List<PetBattleVO> history(Long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        Page<PetBattle> result = battleMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<PetBattle>()
                        .and(q -> q.eq(PetBattle::getAttackerUserId, userId)
                                .or().eq(PetBattle::getDefenderUserId, userId))
                        .orderByDesc(PetBattle::getId));
        return result.getRecords().stream().map(b -> toVo(b, userId, null)).toList();
    }

    @Override
    public int expirePendingBattles() {
        return battleMapper.update(null, new LambdaUpdateWrapper<PetBattle>()
                .set(PetBattle::getStatus, PetBattleStatus.EXPIRED.name())
                .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name())
                .le(PetBattle::getStartedAt, LocalDateTime.now(ZoneId.of("UTC"))
                        .minusHours(properties.getBattle().getPendingExpireHours())));
    }

    // ---------------- 内部共用 ----------------

    /** 新建战斗并立即结算（PvE） */
    private PetBattleVO settleNewBattle(Pet attacker, PetBattleEngine.Fighter defender,
                                        PetBattleMode mode, Long defenderPetId, Long defenderUserId) {
        long seed = secureRandom.nextLong();
        PetBattle battle = buildBattle(attacker, defender, mode, defenderPetId, defenderUserId, seed);

        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(
                toFighter(attacker), defender, seed, properties.getBattle().getMaxRounds());
        int winExp = properties.getBattle().getWinExp();
        int loseExp = properties.getBattle().getLoseExp();
        int expReward = result.attackerWon() ? winExp : loseExp;

        battle.setStatus(PetBattleStatus.FINISHED.name());
        battle.setStartedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setWinnerPetId(result.winnerPetId());
        battle.setRounds(PetJsonUtils.toJson(result.rounds()));
        battle.setExpReward(expReward);
        battle.setCurrencyReward(result.attackerWon() ? properties.getBattle().getWinCurrency() : 0);
        battleMapper.insert(battle);

        grantRewards(attacker.getId(), null, result.attackerWon(), battle);
        return toVo(battle, attacker.getUserId(), result.rounds());
    }

    private PetBattle buildBattle(Pet attacker, PetBattleEngine.Fighter defender, PetBattleMode mode,
                                  Long defenderPetId, Long defenderUserId, long seed) {
        PetBattle battle = new PetBattle();
        battle.setMode(mode.name());
        battle.setAttackerPetId(attacker.getId());
        battle.setAttackerUserId(attacker.getUserId());
        battle.setDefenderPetId(defenderPetId != null ? defenderPetId : 0L);
        battle.setDefenderUserId(defenderUserId);
        battle.setSeed(seed);
        battle.setAttackerSnapshot(PetJsonUtils.toJson(toFighter(attacker)));
        battle.setDefenderSnapshot(PetJsonUtils.toJson(defender));
        battle.setStartedAt(LocalDateTime.now(ZoneId.of("UTC")));
        return battle;
    }

    /**
     * 双方奖励发放：经验（本地乐观锁）+ 胜者星光（Feign，失败抛 503 事务回滚）。
     * 防守方被动应战也有收益（原文档 §1.8：鼓励 accept）。
     */
    private void grantRewards(Long attackerPetId, Long defenderPetId, boolean attackerWon, PetBattle battle) {
        Pet attacker = petMapper.selectById(attackerPetId);
        if (attacker == null) {
            return;
        }
        int attackerExp = attackerWon
                ? properties.getBattle().getWinExp()
                : properties.getBattle().getLoseExp();
        // 三期埋点：亲密度先叠加（与经验同一次写入）
        intimacyService.gain(attacker, PetIntimacySource.BATTLE);
        int levelups = stateService.grantExp(attacker, attackerExp);
        if (levelups > 0) {
            achievementService.evaluate(attacker, PetAchievementService.Event.LEVEL_UP);
            notifyLevelUp(attacker.getUserId(), attacker);
        }
        achievementService.evaluate(attacker, PetAchievementService.Event.BATTLE_FINISHED);
        dailyQuestService.record(attacker, PetQuestType.BATTLE, 1);

        if (attackerWon && battle.getCurrencyReward() != null && battle.getCurrencyReward() > 0) {
            // B01：本地奖励已生效；星光经统一操作记录幂等发放，结果未知不回滚本地奖励
            String operationId = operationService.operationKey("BATTLE_REWARD", battle.getId(), "attacker");
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, battle.getAttackerUserId(), battle.getAttackerPetId(),
                    "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null);
            if (!settlement.isCompleted()) {
                log.info("对战奖励星光结算中, battleId={}, side=attacker, operationId={}",
                        battle.getId(), operationId);
            }
        }

        if (defenderPetId != null && defenderPetId > 0) {
            Pet defender = petMapper.selectById(defenderPetId);
            if (defender != null) {
                int defenderExp = attackerWon
                        ? properties.getBattle().getLoseExp()
                        : properties.getBattle().getWinExp();
                intimacyService.gain(defender, PetIntimacySource.BATTLE);
                int defenderLevelups = stateService.grantExp(defender, defenderExp);
                if (defenderLevelups > 0) {
                    achievementService.evaluate(defender, PetAchievementService.Event.LEVEL_UP);
                    notifyLevelUp(defender.getUserId(), defender);
                }
                achievementService.evaluate(defender, PetAchievementService.Event.BATTLE_FINISHED);
                dailyQuestService.record(defender, PetQuestType.BATTLE, 1);
                // 两只宠物若已建立关系：对战给关系加亲密度（原文档三期宠物关系）
                relationService.gainBetween(attacker, defender, PetRelationAction.BATTLE);
                if (!attackerWon && battle.getCurrencyReward() != null && battle.getCurrencyReward() > 0) {
                    String operationId = operationService.operationKey("BATTLE_REWARD", battle.getId(), "defender");
                    PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                            operationId, defender.getUserId(), defender.getId(),
                            "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null);
                    if (!settlement.isCompleted()) {
                        log.info("对战奖励星光结算中, battleId={}, side=defender, operationId={}",
                                battle.getId(), operationId);
                    }
                }
            }
        }
        // PvE 结算即通知挑战方；PvP 结果由 accept 时分别通知双方（B08：各一次，eventId 去重）
        if (PetBattleMode.PVE.name().equals(battle.getMode()) || battle.getDefenderUserId() == null) {
            eventProducer.publish(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                    "BATTLE_FINISHED:" + battle.getId() + ":attacker",
                    String.valueOf(battle.getAttackerUserId()), "PET_BATTLE_FINISHED",
                    resultAttackerWon(attackerWon),
                    battleRewardText(battle, attackerWon),
                    String.valueOf(battle.getId()), "PET_BATTLE_FINISHED"));
        } else if (battle.getDefenderUserId() != null && defenderPetId != null && defenderPetId > 0) {
            boolean defenderWon = !attackerWon;
            eventProducer.publish(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                    "BATTLE_FINISHED:" + battle.getId() + ":defender",
                    String.valueOf(battle.getDefenderUserId()), "PET_BATTLE_FINISHED",
                    resultAttackerWon(defenderWon),
                    battleRewardText(battle, defenderWon),
                    String.valueOf(battle.getId()), "PET_BATTLE_FINISHED"));
        }
    }

    private String resultAttackerWon(boolean attackerWon) {
        return attackerWon ? "对战大获全胜！" : "对战惜败，下次再战！";
    }

    private String battleRewardText(PetBattle battle, boolean attackerWon) {
        int exp = attackerWon ? properties.getBattle().getWinExp() : properties.getBattle().getLoseExp();
        return (attackerWon ? "赢得了对战，获得 " + exp + " 点经验和 " + properties.getBattle().getWinCurrency()
                + " 星光！" : "获得 " + exp + " 点经验，继续加油！");
    }

    private void notifyLevelUp(Long userId, Pet pet) {
        eventProducer.publish(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel(),
                String.valueOf(userId), "PET_LEVEL_UP",
                "宠物升级啦！",
                pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                String.valueOf(pet.getId()), "PET_LEVEL_UP"));
    }

    @Override
    public List<PetBattleVO> pending(Long userId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        Page<PetBattle> result = battleMapper.selectPage(new Page<>(Math.max(page, 1), pageSize),
                new LambdaQueryWrapper<PetBattle>()
                        .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name())
                        .and(w -> w.eq(PetBattle::getAttackerUserId, userId)
                                .or().eq(PetBattle::getDefenderUserId, userId))
                        .orderByDesc(PetBattle::getId));
        return result.getRecords().stream()
                .map(battle -> toVo(battle, userId, null))
                .toList();
    }

    private PetBattle requireBattle(Long battleId) {
        PetBattle battle = battleMapper.selectById(battleId);
        if (battle == null) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_NOT_FOUND, "对战记录不存在");
        }
        return battle;
    }

    /** 随机偏移抽样候选对手（B22）：先 count 再 LIMIT offset，避免全表 RAND() 排序 */
    private List<Pet> sampleRivals(Long userId, Pet pet) {
        LambdaQueryWrapper<Pet> range = new LambdaQueryWrapper<Pet>()
                .ne(Pet::getUserId, userId)
                .eq(Pet::getIsPublic, true)
                .between(Pet::getLevel, Math.max(1, pet.getLevel() - 5), pet.getLevel() + 5);
        long total = petMapper.selectCount(range);
        if (total == 0) {
            return List.of();
        }
        int limit = OPPONENT_LIMIT;
        long offset = secureRandom.nextLong(Math.min(total, 200));
        List<Pet> sampled = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .ne(Pet::getUserId, userId)
                .eq(Pet::getIsPublic, true)
                .between(Pet::getLevel, Math.max(1, pet.getLevel() - 5), pet.getLevel() + 5)
                .orderByAsc(Pet::getId)
                .last("LIMIT " + limit + " OFFSET " + offset));
        if (sampled.size() < limit && offset > 0) {
            sampled = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                    .ne(Pet::getUserId, userId)
                    .eq(Pet::getIsPublic, true)
                    .between(Pet::getLevel, Math.max(1, pet.getLevel() - 5), pet.getLevel() + 5)
                    .orderByAsc(Pet::getId)
                    .last("LIMIT " + limit));
        }
        return sampled;
    }

    /** PvE 野生模板名（与 wildOpponents 一一对应，templateId 1-3） */
    private static final String[] WILD_TEMPLATE_NAMES = {"野猫小灰", "野犬阿黄", "野兔速速"};

    /**
     * PvE 野生宠物模板（B08 稳定模板）：名称/等级/属性全部由 (templateId, 挑战者等级) 确定，
     * 与 /battle/opponents 展示完全一致——选择哪只就挑战哪只，服务端不再随机另一只。
     */
    private PetBattleEngine.Fighter wildFighter(int attackerLevel, int templateId) {
        int index = Math.max(1, Math.min(WILD_TEMPLATE_NAMES.length, templateId)) - 1;
        int level = Math.max(1, attackerLevel + index - 1);
        return new PetBattleEngine.Fighter(0L, WILD_TEMPLATE_NAMES[index] + " Lv." + level,
                100 + level * 5, 100 + level * 5,
                5 + level, 5 + level, 5 + level, 5 + level);
    }

    private List<PetOpponentVO> wildOpponents(Pet pet) {
        List<PetOpponentVO> wilds = new ArrayList<>();
        for (int i = 0; i < WILD_OPPONENT_COUNT; i++) {
            int templateId = i + 1;
            int level = Math.max(1, pet.getLevel() + i - 1);
            wilds.add(new PetOpponentVO(0L, WILD_TEMPLATE_NAMES[i] + " Lv." + level, "WILD", level, "WILD",
                    true, null, WILD_OWNER_PLACEHOLDER, templateId));
        }
        return wilds;
    }

    private PetBattleMode parseMode(String mode) {
        try {
            return PetBattleMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_MODE_INVALID, "对战模式非法");
        }
    }

    /** 快照参战者：基础属性 + 装备加成 + 技能效果（原文档 §89：装备/技能在战斗中生效） */
    private PetBattleEngine.Fighter toFighter(Pet pet) {
        PetStatsService.CombatStats stats = statsService.combatStats(pet);
        return new PetBattleEngine.Fighter(pet.getId(), pet.getName(),
                stats.hp(), stats.maxHp(),
                stats.strength(), stats.intelligence(), stats.agility(), stats.charm(),
                stats.critBonus(), stats.dodgeBonus(), stats.damageBonus(),
                stats.powerStrikeBonus(), stats.damageReduction(), statsService.firstStrikeBonus(pet));
    }

    private Map<Long, String> resolveNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> users = wishFeignClient.batchGetUsers(userIds).data();
            if (users == null) {
                return Map.of();
            }
            Map<Long, String> result = new java.util.HashMap<>();
            for (Map<String, Object> user : users) {
                Object id = user.get("id");
                Object nickname = user.get("nickname");
                if (id instanceof Number numberId && nickname != null) {
                    result.put(numberId.longValue(), nickname.toString());
                }
            }
            return result;
        } catch (Exception e) {
            // 昵称是展示型数据：Fail-Open 占位
            return Map.of();
        }
    }

    private PetBattleVO toVo(PetBattle battle, Long viewerUserId, List<PetBattleEngine.Round> rounds) {
        String role = viewerUserId != null && viewerUserId.equals(battle.getDefenderUserId())
                ? "DEFENDER" : "ATTACKER";
        String roundsJson = rounds != null ? PetJsonUtils.toJson(rounds) : battle.getRounds();
        return new PetBattleVO(battle.getId(), battle.getMode(), battle.getStatus(), role,
                battle.getAttackerPetId(), fighterName(battle.getAttackerSnapshot()), battle.getAttackerUserId(),
                battle.getDefenderPetId(), fighterName(battle.getDefenderSnapshot()), battle.getDefenderUserId(),
                battle.getWinnerPetId(), roundsJson,
                battle.getExpReward(), battle.getCurrencyReward(),
                battle.getStartedAt(), battle.getFinishedAt());
    }

    /** 从结算快照还原对手显示名（PvP 对手宠物可能已改名/放生，因此必须以快照为准） */
    private String fighterName(String snapshotJson) {
        PetBattleEngine.Fighter fighter = PetJsonUtils.parse(snapshotJson,
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        return fighter == null ? null : fighter.name();
    }
}
