package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ChallengeBattleRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.enums.PetBattleMode;
import com.cloudmart.pet.enums.PetBattleStatus;
import com.cloudmart.pet.feign.UserFeignClient;
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
import java.time.ZoneOffset;
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

    /** P1-3：发起方放弃（被拒/过期）冷却阈值——24h 内达阈值后禁止再发起 PvP */
    static final int ABORT_COOLDOWN_LIMIT = 3;
    /** PENDING 期 seed 哨兵值：seed 延迟到 accept 生成，发起时不可预测（列 NOT NULL 用 0 占位） */
    static final long SEED_PENDING = 0L;

    private final PetService petService;
    private final PetStateService stateService;
    private final PetBattleMapper battleMapper;
    private final PetMapper petMapper;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final WishFeignClient wishFeignClient;
    private final com.cloudmart.pet.feign.UserFeignClient userFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final PetStatsService statsService;
    private final PetDailyQuestService dailyQuestService;
    /** R05：社交写入门控 */
    private final PetAccessPolicy accessPolicy;
    private final PetIntimacyService intimacyService;
    private final PetRelationService relationService;
    private final PetEconomyService economyService;
    private final PetQuotaService quotaService;
    private final PetRankingCache rankingCache;
    private final PetFriendFeedService friendFeedService;
    private final com.cloudmart.pet.repository.PetActivityMapper activityMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PetBattleServiceImpl(PetService petService,
                                PetStateService stateService,
                                PetBattleMapper battleMapper,
                                PetMapper petMapper,
                                WishFeignClient wishFeignClient,
                                UserFeignClient userFeignClient,
                                PetAchievementService achievementService,
                                PetEventProducer eventProducer,
                                PetProperties properties,
                                PetStatsService statsService,
                                PetDailyQuestService dailyQuestService,
                                PetIntimacyService intimacyService,
                                PetRelationService relationService,
                                PetEconomyService economyService,
                                com.cloudmart.pet.service.PetUserBlockService userBlockService,
                                PetQuotaService quotaService,
                                PetRankingCache rankingCache,
                                PetFriendFeedService friendFeedService,
                                com.cloudmart.pet.repository.PetActivityMapper activityMapper,
        PetAccessPolicy accessPolicy) {
        this.petService = petService;
        this.stateService = stateService;
        this.battleMapper = battleMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.userFeignClient = userFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.properties = properties;
        this.statsService = statsService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.relationService = relationService;
        this.economyService = economyService;
        this.userBlockService = userBlockService;
        this.quotaService = quotaService;
        this.rankingCache = rankingCache;
        this.friendFeedService = friendFeedService;
        this.activityMapper = activityMapper;
        this.accessPolicy = accessPolicy;
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
            List<Pet> fallback = PetCandidateSampler.sample(petMapper, userId,
                    OPPONENT_LIMIT - rivals.size(), null, null, pickedIds);
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
        // R05：SOCIAL_MUTE 处罚生效时禁止发起新对战
        if (accessPolicy.isSociallyMuted(userId)) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED,
                    "你的账号因违反社区规范被限制对战，如有疑问请联系客服申诉");
        }
        Pet attacker = petService.requireOwnedPet(userId);
        // F4：虚弱状态禁止对战
        if (stateService.isWeak(attacker)) {
            throw new BusinessException(PetErrorCodes.PET_STATE_WEAK, "宠物饿坏了上不了战场，先喂点东西吧");
        }
        // P04：出战/工作互斥——攻击者打工/学习中不能发起挑战
        assertNotBusyWithActivity(userId, attacker.getId(), "出战");
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
            throw new BusinessException(PetErrorCodes.PET_BATTLE_SELF_CHALLENGE, "不能挑战自己的宠物哦");
        }
        if (userBlockService.isBlockedEitherWay(userId, defender.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法挑战该用户");
        }

        // P1-3：放弃冷却——24h（当日业务日）内被拒/过期 ≥3 次禁止再发起，防 PENDING 期内无成本刷发起
        if (quotaService.used(userId, PetQuotaService.QuotaType.BATTLE_ABORT, 0) >= ABORT_COOLDOWN_LIMIT) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_CONFLICT,
                    "对战发起太频繁啦，明天再约战吧");
        }

        // P1-3：seed 延迟到接受时生成（挑战者/防守方均不可预知，无法本地重放筛局）；
        // seed 列 NOT NULL，PENDING 期以 0 占位（seed 不经任何 API 外泄）
        PetBattle battle = buildBattle(attacker, toFighter(defender), PetBattleMode.PVP,
                defender.getId(), defender.getUserId(), SEED_PENDING);
        battle.setStatus(PetBattleStatus.PENDING.name());
        battleMapper.insert(battle);

        eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                "BATTLE_CHALLENGE:" + battle.getId(),
                String.valueOf(defender.getUserId()), "PET_BATTLE_CHALLENGE",
                "有人向我发起挑战啦！",
                attacker.getName() + " 向 " + defender.getName() + " 发起了对战挑战，去应战吧！",
                String.valueOf(battle.getId()), "PET_BATTLE_CHALLENGE"));
        return toVo(battle, userId, null);
    }

    /**
     * P04：出战/工作互斥——参战宠物处于打工/学习进行中时禁止开战
     * （挑战创建与应战两侧同样生效，防"PENDING 期间开工"的窗口）。
     */
    private void assertNotBusyWithActivity(Long userId, Long petId, String role) {
        Long busy = activityMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetActivity>()
                .eq(com.cloudmart.pet.entity.PetActivity::getUserId, userId)
                .eq(com.cloudmart.pet.entity.PetActivity::getPetId, petId)
                .eq(com.cloudmart.pet.entity.PetActivity::getStatus,
                        com.cloudmart.pet.enums.PetActivityStatus.IN_PROGRESS.name()));
        if (busy > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "宠物正在打工/学习中，无法" + role);
        }
    }

    @Override
    @Transactional
    public PetBattleVO accept(Long userId, Long battleId) {
        PetBattle battle = requireBattle(battleId);
        if (!userId.equals(battle.getDefenderUserId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只有被挑战方可以应战");
        }
        // R38：CAS 带到期条件——过期挑战按时间直接拒绝（原实现仅 status=PENDING，
        // 清理器未运行前仍可应战旧局，等待期间的配置/屏蔽变化被绕过）
        int updated = battleMapper.update(null, new LambdaUpdateWrapper<PetBattle>()
                .set(PetBattle::getStatus, PetBattleStatus.FINISHED.name())
                .set(PetBattle::getFinishedAt, LocalDateTime.now(ZoneOffset.UTC))
                .eq(PetBattle::getId, battleId)
                .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name())
                .gt(PetBattle::getStartedAt, LocalDateTime.now(ZoneOffset.UTC)
                        .minusHours(properties.getBattle().getPendingExpireHours())));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_BATTLE_ALREADY_HANDLED, "这场挑战已经被处理过啦");
        }

        // P04：互斥复检——PENDING 期间任一方开始打工/学习，应战时拒绝（挑战作废，
        // 由"已被处理"语义兜底；发起/应战两侧与创建时同样生效）
        assertNotBusyWithActivity(battle.getAttackerUserId(), battle.getAttackerPetId(), "出战");
        assertNotBusyWithActivity(userId, battle.getDefenderPetId(), "应战");

        PetBattleEngine.Fighter attacker = PetJsonUtils.parse(battle.getAttackerSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        PetBattleEngine.Fighter defender = PetJsonUtils.parse(battle.getDefenderSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        // P1-3：seed 在接受时才生成——挑战者发起时（乃至 PENDING 全程）都无法预知对局随机源
        long seed = secureRandom.nextLong();
        battle.setSeed(seed);
        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(
                attacker, defender, seed, properties.getBattle().getMaxRounds());

        battle.setStatus(PetBattleStatus.FINISHED.name());
        battle.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setWinnerPetId(result.winnerPetId());
        battle.setRounds(PetJsonUtils.toJson(result.rounds()));
        // P1-3：每日收益场次/对同一对手收益配额（占满则该方 0 收益，参战与结算不受影响）
        boolean attackerRewarded = tryConsumeRewardQuota(battle.getAttackerUserId(), battle.getDefenderUserId());
        boolean defenderRewarded = tryConsumeRewardQuota(battle.getDefenderUserId(), battle.getAttackerUserId());
        int winExp = properties.getBattle().getWinExp();
        int loseExp = properties.getBattle().getLoseExp();
        int expReward = attackerRewarded ? (result.attackerWon() ? winExp : loseExp) : 0;
        battle.setExpReward(expReward);
        battle.setCurrencyReward(attackerRewarded && result.attackerWon()
                ? properties.getBattle().getWinCurrency() : 0);
        battleMapper.updateById(battle);

        grantRewards(attacker.petId(), defender.petId(), result.attackerWon(), battle,
                attackerRewarded, defenderRewarded);
        return toVo(battle, userId, result.rounds());
    }

    /**
     * 收益配额（P1-3）：BATTLE_REWARD = 每日<b>总</b>收益场次（targetId 固定 0，不按对手拆分，
     * 多号互刷无法绕过）；PvP 另限对同一对手用户的收益场次（targetId = 对手 userId）。
     * 任一条件不满足即该方本场合计 0 收益；已占用的总场次名额回退，避免白吃额度。
     */
    private boolean tryConsumeRewardQuota(Long userId, Long opponentUserId) {
        if (userId == null) {
            return false;
        }
        if (!quotaService.tryConsume(userId, PetQuotaService.QuotaType.BATTLE_REWARD, 0,
                properties.getBattle().getRewardDailyLimit())) {
            return false;
        }
        if (opponentUserId != null && opponentUserId > 0
                && !quotaService.tryConsume(userId, PetQuotaService.QuotaType.PVP_OPPONENT,
                        opponentUserId, properties.getBattle().getPvpPerOpponentDailyLimit())) {
            quotaService.release(userId, PetQuotaService.QuotaType.BATTLE_REWARD, 0);
            return false;
        }
        return true;
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
        // P1-3：防守方拒绝计入发起方"放弃"累计（冷却防反复发起）
        quotaService.record(battle.getAttackerUserId(), PetQuotaService.QuotaType.BATTLE_ABORT, 0);
        eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
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
        // P1-3：过期同样计入发起方"放弃"累计——改为逐行 CAS 流转（过期行按 id 确定性排序，
        // 多实例重扫由 CAS 兜底），并在同一业务日内累计 BATTLE_ABORT 配额
        List<PetBattle> stale = battleMapper.selectList(new LambdaQueryWrapper<PetBattle>()
                .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name())
                .le(PetBattle::getStartedAt, LocalDateTime.now(ZoneId.of("UTC"))
                        .minusHours(properties.getBattle().getPendingExpireHours()))
                .orderByAsc(PetBattle::getId)
                .last("LIMIT 200"));
        int expired = 0;
        for (PetBattle battle : stale) {
            int updated = battleMapper.update(null, new LambdaUpdateWrapper<PetBattle>()
                    .set(PetBattle::getStatus, PetBattleStatus.EXPIRED.name())
                    .eq(PetBattle::getId, battle.getId())
                    .eq(PetBattle::getStatus, PetBattleStatus.PENDING.name()));
            if (updated > 0) {
                expired++;
                if (battle.getAttackerUserId() != null) {
                    quotaService.record(battle.getAttackerUserId(),
                            PetQuotaService.QuotaType.BATTLE_ABORT, 0);
                }
            }
        }
        return expired;
    }

    // ---------------- 内部共用 ----------------

    /** 新建战斗并立即结算（PvE） */
    private PetBattleVO settleNewBattle(Pet attacker, PetBattleEngine.Fighter defender,
                                        PetBattleMode mode, Long defenderPetId, Long defenderUserId) {
        long seed = secureRandom.nextLong();
        PetBattle battle = buildBattle(attacker, defender, mode, defenderPetId, defenderUserId, seed);

        PetBattleEngine.BattleResult result = PetBattleEngine.simulate(
                toFighter(attacker), defender, seed, properties.getBattle().getMaxRounds());
        // P1-3：PvE 同样受每日总收益场次配额约束（targetId=0）
        boolean attackerRewarded = tryConsumeRewardQuota(attacker.getUserId(), null);
        int winExp = properties.getBattle().getWinExp();
        int loseExp = properties.getBattle().getLoseExp();
        int expReward = attackerRewarded ? (result.attackerWon() ? winExp : loseExp) : 0;

        battle.setStatus(PetBattleStatus.FINISHED.name());
        battle.setStartedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")));
        battle.setWinnerPetId(result.winnerPetId());
        battle.setRounds(PetJsonUtils.toJson(result.rounds()));
        battle.setExpReward(expReward);
        // R38：PvE 失败局不写胜利币（原实现有收益额度即写 winCurrency，显示"获得星光"实际失败局不发）
        battle.setCurrencyReward(attackerRewarded && result.attackerWon()
                ? properties.getBattle().getWinCurrency() : 0);
        // B08：PVP 星光归胜者（防守方获胜同样得奖，不依赖挑战者字段）；PVE 保持仅挑战方胜出有奖
        battleMapper.insert(battle);

        grantRewards(attacker.getId(), null, result.attackerWon(), battle, attackerRewarded, false);
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
     * P1-3：收益配额占满的一方只结算 0 收益（attackerRewarded/defenderRewarded 控制实际发放）。
     */
    private void grantRewards(Long attackerPetId, Long defenderPetId, boolean attackerWon, PetBattle battle,
                              boolean attackerRewarded, boolean defenderRewarded) {
        Pet attacker = petMapper.selectById(attackerPetId);
        if (attacker == null) {
            return;
        }
        // P1-4：胜场榜 ZSet 埋点——与 DB 聚合同口径（任何 FINISHED 对战中真实获胜的宠物 +1）
        Long winnerPetId = attackerWon ? attackerPetId
                : (defenderPetId != null && defenderPetId > 0 ? defenderPetId : null);
        // R38：胜场榜只计有收益局的胜利（练习局不上榜，scoreEligible 语义）
        boolean winnerScoreEligible = (attackerWon && attackerRewarded)
                || (!attackerWon && defenderRewarded);
        if (winnerPetId != null && winnerPetId > 0 && winnerScoreEligible) {
            rankingCache.onBattleWin(winnerPetId);
            // F3：获胜动态扇出（胜者行即持有主人 userId）
            Pet winner = petMapper.selectById(winnerPetId);
            if (winner != null && winner.getUserId() != null) {
                friendFeedService.append(winner.getUserId(), winnerPetId,
                        PetFriendFeedService.EVENT_BATTLE_WIN,
                        winner.getName() + " 在对战中获胜！", winner.getName());
            }
        }
        int attackerExp = attackerRewarded
                ? (attackerWon ? properties.getBattle().getWinExp() : properties.getBattle().getLoseExp())
                : 0;
        // R38：练习局（收益额度耗尽）不加亲密/任务/成就——参战与结算不受影响，
        // 但无收益（16.3 练习与收益分离；原实现收益额度不能约束亲密度/任务）
        if (attackerRewarded) {
            intimacyService.gain(attacker, PetIntimacySource.BATTLE);
            dailyQuestService.record(attacker, PetQuestType.BATTLE, 1);
            achievementService.evaluate(attacker, PetAchievementService.Event.BATTLE_FINISHED);
        }
        int levelups = stateService.grantExp(attacker, attackerExp);
        if (levelups > 0) {
            achievementService.evaluate(attacker, PetAchievementService.Event.LEVEL_UP);
            notifyLevelUp(attacker.getUserId(), attacker);
        }

        if (attackerRewarded && attackerWon && battle.getCurrencyReward() != null && battle.getCurrencyReward() > 0) {
            // B01：本地奖励已生效；星光经统一操作记录幂等发放，结果未知不回滚本地奖励
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    battle.getAttackerUserId(), battle.getAttackerPetId(),
                    "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null,
                    battle.getId(), "attacker");
            if (!settlement.isCompleted()) {
                log.info("对战奖励星光结算中, battleId={}, side=attacker, status={}",
                        battle.getId(), settlement.status());
            }
        }

        if (defenderPetId != null && defenderPetId > 0) {
            Pet defender = petMapper.selectById(defenderPetId);
            if (defender != null) {
                int defenderExp = defenderRewarded
                        ? (attackerWon ? properties.getBattle().getLoseExp() : properties.getBattle().getWinExp())
                        : 0;
                // R38：防守方练习局同口径（不加亲密/任务/成就）
                if (defenderRewarded) {
                    intimacyService.gain(defender, PetIntimacySource.BATTLE);
                    dailyQuestService.record(defender, PetQuestType.BATTLE, 1);
                    achievementService.evaluate(defender, PetAchievementService.Event.BATTLE_FINISHED);
                }
                int defenderLevelups = stateService.grantExp(defender, defenderExp);
                if (defenderLevelups > 0) {
                    achievementService.evaluate(defender, PetAchievementService.Event.LEVEL_UP);
                    notifyLevelUp(defender.getUserId(), defender);
                }
                // 两只宠物若已建立关系：对战给关系加亲密度（原文档三期宠物关系）
                relationService.gainBetween(attacker, defender, PetRelationAction.BATTLE);
                if (defenderRewarded && !attackerWon && battle.getCurrencyReward() != null && battle.getCurrencyReward() > 0) {
                    PetEconomyService.WalletSettlement settlement = economyService.earn(
                            defender.getUserId(), defender.getId(),
                            "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null,
                            battle.getId(), "defender");
                    if (!settlement.isCompleted()) {
                        log.info("对战奖励星光结算中, battleId={}, side=defender, status={}",
                                battle.getId(), settlement.status());
                    }
                }
            }
        }
        // PvE 结算即通知挑战方；PvP 结果由 accept 时分别通知双方（B08：各一次，eventId 去重）
        if (PetBattleMode.PVE.name().equals(battle.getMode()) || battle.getDefenderUserId() == null) {
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                    "BATTLE_FINISHED:" + battle.getId() + ":attacker",
                    String.valueOf(battle.getAttackerUserId()), "PET_BATTLE_FINISHED",
                    resultAttackerWon(attackerWon),
                    battleRewardText(battle, attackerWon, attackerRewarded),
                    String.valueOf(battle.getId()), "PET_BATTLE_FINISHED"));
        } else if (battle.getDefenderUserId() != null && defenderPetId != null && defenderPetId > 0) {
            boolean defenderWon = !attackerWon;
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_BATTLE_FINISHED, new PetEventProducer.PetEventMessage(
                    "BATTLE_FINISHED:" + battle.getId() + ":defender",
                    String.valueOf(battle.getDefenderUserId()), "PET_BATTLE_FINISHED",
                    resultAttackerWon(defenderWon),
                    battleRewardText(battle, defenderWon, defenderRewarded),
                    String.valueOf(battle.getId()), "PET_BATTLE_FINISHED"));
        }
    }

    private String resultAttackerWon(boolean attackerWon) {
        return attackerWon ? "对战大获全胜！" : "对战惜败，下次再战！";
    }

    private String battleRewardText(PetBattle battle, boolean attackerWon, boolean rewarded) {
        if (!rewarded) {
            return "今天和对战相关的收益场次已用完，这次没有奖励啦，明天继续加油！";
        }
        int exp = attackerWon ? properties.getBattle().getWinExp() : properties.getBattle().getLoseExp();
        return (attackerWon ? "赢得了对战，获得 " + exp + " 点经验和 " + properties.getBattle().getWinCurrency()
                + " 星光！" : "获得 " + exp + " 点经验，继续加油！");
    }

    private void notifyLevelUp(Long userId, Pet pet) {
        eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
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
            List<Map<String, Object>> users = userFeignClient.batchGetUsers(userIds).data();
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
