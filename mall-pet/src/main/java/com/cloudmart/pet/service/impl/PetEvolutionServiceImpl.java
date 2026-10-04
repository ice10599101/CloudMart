package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetEvolutionService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetEvolutionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物进化实现。
 *
 * <p>顺序（B01）：校验（等级/当前阶段）→ 幂等扣款（operationId = EVOLVE:petId:目标阶段，
 * 阶段转换一次性，键天然唯一）→ 本地应用进化（属性/皮肤/活动留痕）。扣款结果未知抛
 * PET_SETTLEMENT_PENDING；扣款成功但崩溃由恢复任务按快照幂等补应用（以当前阶段判定），
 * 永久无法履约按原单退款。进化链取自配置表，禁止硬编码阶段数值。</p>
 */
@Service
@Slf4j
public class PetEvolutionServiceImpl implements PetEvolutionService {

    private static final int ATTRIBUTE_MAX = 999;
    private static final String BIZ_TYPE = "EVOLVE";
    /** R28：进化请求去重作用域 */
    static final String ENDPOINT_EVOLUTION = "EVOLUTION";

    private final PetService petService;
    private final PetEvolutionConfigMapper evolutionConfigMapper;
    private final PetMapper petMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetActivityMapper activityMapper;
    private final PetAchievementService achievementService;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService;
    private final PetOutboxService outboxService;
    private final PetClock petClock;
    private final com.cloudmart.pet.wallet.PetRequestDedupService dedupService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public PetEvolutionServiceImpl(PetService petService,
                                   PetEvolutionConfigMapper evolutionConfigMapper,
                                   PetMapper petMapper,
                                   PetInventoryMapper inventoryMapper,
                                   PetActivityMapper activityMapper,
                                   PetAchievementService achievementService,
                                   PetEconomyService economyService,
                                   com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService,
                                   PetOutboxService outboxService,
                                   PetClock petClock,
                                   com.cloudmart.pet.wallet.PetRequestDedupService dedupService,
                                   org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        this.petService = petService;
        this.evolutionConfigMapper = evolutionConfigMapper;
        this.petMapper = petMapper;
        this.inventoryMapper = inventoryMapper;
        this.activityMapper = activityMapper;
        this.achievementService = achievementService;
        this.economyService = economyService;
        this.playFeatureService = playFeatureService;
        this.outboxService = outboxService;
        this.petClock = petClock;
        this.dedupService = dedupService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public PetEvolutionVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return buildStatus(pet, balanceQuietly(userId));
    }

    @Override
    public PetEvolutionVO evolve(Long userId) {
        return evolve(userId, null, null);
    }

    /**
     * R28 意图冻结进化（§5.3 编排：本方法是意图事务边界，调用方不得包更大事务）：
     * <ol>
     *   <li>必需幂等键 → dedup.claim 作用域 EVOLUTION（同键异参 409，处理中 409）；</li>
     *   <li>EXISTING 重放：直接返回原进化结果，不重读当前阶段、不重新选 nextConfig——
     *       修复"重试被解释为购买下一阶段"（同键 50 次仅推进一阶、扣一笔）；</li>
     *   <li>NEW：业务事务内冻结 fromStage→stageTo 与价格/属性快照；扣款业务键含
     *       fromStage（等待期阶段变化不影响收敛）；阶段推进带
     *       {@code WHERE evolution_stage=fromStage} 条件更新；扣款/推进/解锁/事实/事件同事务。</li>
     * </ol>
     */
    @Override
    public PetEvolutionVO evolve(Long userId, Long petId, Integer expectedFromStage) {
        String requestKey = PetRequestContext.idempotencyKey();
        if (!PetRequestDedupService.isValidRequestKey(requestKey)) {
            throw new BusinessException(PetErrorCodes.PET_REQUEST_KEY_INVALID,
                    "缺少有效幂等键（16..128 ASCII），请重试一次由客户端生成");
        }
        String payloadHash = dedupService.canonicalHash(userId, petId == null ? "BIND" : petId,
                "EVOLVE", expectedFromStage == null ? "" : expectedFromStage);
        PetRequestDedupService.ClaimResult claim = dedupService.claim(
                userId, ENDPOINT_EVOLUTION, requestKey, payloadHash);
        switch (claim.outcome()) {
            case EXISTING -> {
                return replayResult(claim.responseJson());
            }
            case IN_PROGRESS -> throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS,
                    "进化请求处理中，请稍后按原请求查询结果");
            case NEW -> {
                // 继续执行
            }
        }
        if (petId == null) {
            petId = claim.boundPetId() != null ? claim.boundPetId() : petService.requireOwnedPet(userId).getId();
            dedupService.bindPet(userId, ENDPOINT_EVOLUTION, requestKey, petId);
        }
        final Long boundPetId = petId;
        final Integer expectedStage = expectedFromStage;
        final String leaseOwner = claim.leaseOwner();
        try {
            PetEvolutionVO result = transactionTemplate.execute(status ->
                    doEvolve(userId, boundPetId, expectedStage, requestKey));
            dedupService.completeSucceeded(userId, ENDPOINT_EVOLUTION, requestKey, leaseOwner, boundPetId,
                    PetJsonUtils.toJson(java.util.Map.of("type", "SUCCEEDED", "vo", result)));
            return result;
        } catch (BusinessException definite) {
            // 业务明确拒绝（等级/满阶/余额/版本）：终态保存拒绝，同键重放返回同一拒绝而非重新执行
            dedupService.completeSucceeded(userId, ENDPOINT_EVOLUTION, requestKey, leaseOwner, boundPetId,
                    PetJsonUtils.toJson(java.util.Map.of("type", "REJECTED", "errorCode", definite.getCode())));
            throw definite;
        } catch (RuntimeException unknown) {
            // 业务事务已整体回滚，本地无已提交事实；钱包若已扣款由同键重放/恢复任务按原 operationId 收敛
            log.error("进化事务未知失败, userId={}, petId={}", userId, boundPetId, unknown);
            dedupService.markFailed(userId, ENDPOINT_EVOLUTION, requestKey, leaseOwner,
                    PetJsonUtils.toJson(java.util.Map.of("error", String.valueOf(unknown.getMessage()))));
            throw unknown;
        }
    }

    /** 同键重放：成功返回原 VO，拒绝重抛原错误码（重放语义与首次一致） */
    private PetEvolutionVO replayResult(String responseJson) {
        Map<String, Object> envelope = PetJsonUtils.parse(responseJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        if (envelope == null) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "历史进化结果快照损坏");
        }
        if ("REJECTED".equals(envelope.get("type"))) {
            throw new BusinessException(String.valueOf(envelope.get("errorCode")), "本次进化此前已被拒绝");
        }
        PetEvolutionVO stored = PetJsonUtils.parse(PetJsonUtils.toJson(envelope.get("vo")),
                new com.fasterxml.jackson.core.type.TypeReference<PetEvolutionVO>() {
                });
        if (stored == null) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "历史进化结果快照损坏");
        }
        return stored;
    }

    /** 业务事务：冻结阶段与价格快照 → 幂等扣款 → 条件推进阶段 → 解锁/事实/事件 */
    private PetEvolutionVO doEvolve(Long userId, Long petId, Integer expectedFromStage, String requestKey) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能进化自己的宠物");
        }
        int fromStage = currentStage(pet);
        if (expectedFromStage != null && expectedFromStage != fromStage) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "进化阶段已变化，请刷新后重新确认");
        }
        List<PetEvolutionConfig> configs = enabledConfigs();
        PetEvolutionConfig next = nextConfig(configs, fromStage);
        if (next == null) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_MAX, "宠物已经进化到最高阶段啦");
        }
        int requiredLevel = next.getRequiredLevel() != null ? next.getRequiredLevel() : 1;
        if (pet.getLevel() < requiredLevel) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED,
                    "等级达到 Lv." + requiredLevel + " 才能进化哦");
        }

        // 1. 幂等扣款：业务键冻结 fromStage+stageTo（同键重试收敛原单；UNKNOWN 按原请求重试）
        int cost = orZero(next.getCostStarlight());
        if (cost > 0) {
            PetEconomyService.WalletSettlement settlement = economyService.spend(
                    userId, pet.getId(), BIZ_TYPE, pet.getId(), cost, snapshot(pet, next, fromStage, cost),
                    userId, pet.getId(), fromStage, next.getStageTo());
            if (settlement.isUnknown()) {
                throw economyService.settlementPending();
            }
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "星光扣款未完成: " + settlement.lastError());
            }
        }

        // 2. 条件推进阶段（属性一次性提升 + 阶段 CAS + 可选皮肤解锁）
        applyEvolution(pet, next, fromStage);
        // B02/BE-12：进化事实接入图鉴投影（阶段条目）
        playFeatureService.unlockCollection(userId, pet.getId(),
                "SPECIES", pet.getSpecies() + ":S" + next.getStageTo(),
                "EVOLVE:" + pet.getId() + ":" + next.getStageTo());
        recordEvolutionActivity(pet);

        achievementService.evaluate(pet, PetAchievementService.Event.EVOLUTION);
        String eventId = "EVOLVED:" + pet.getId() + ":" + next.getStageTo();
        outboxService.record(eventId, RocketMQConfig.PET_TAG_EVOLVED, userId, pet.getId(),
                new com.cloudmart.pet.mq.PetEventProducer.PetEventMessage(
                        eventId, String.valueOf(userId), "PET_EVOLVED",
                        "宠物进化啦！",
                        pet.getName() + " 完成了「" + next.getName() + "」，快去看看它的新样子吧！",
                        String.valueOf(pet.getId()), "PET_EVOLVED"));
        return buildStatus(pet, balanceQuietly(userId));
    }

    /** 进化本地效果（R28 条件更新）：{@code WHERE evolution_stage=fromStage AND version=?}——
     * 阶段不符（并发已推进）显式失败整体回滚；重复应用不再以"当前阶段>=目标"静默跳过 */
    private void applyEvolution(Pet pet, PetEvolutionConfig next, int fromStage) {
        int stageTo = orZero(next.getStageTo());
        int newMaxHp = pet.getMaxHp() + orZero(next.getBonusMaxHp());
        int newHp = Math.min(newMaxHp, pet.getHp() + orZero(next.getBonusMaxHp()));
        int updated = petMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Pet>()
                .set(Pet::getEvolutionStage, stageTo)
                .set(Pet::getMaxHp, newMaxHp)
                .set(Pet::getHp, newHp)
                .set(Pet::getStrength, grow(pet.getStrength(), next.getBonusStrength()))
                .set(Pet::getIntelligence, grow(pet.getIntelligence(), next.getBonusIntelligence()))
                .set(Pet::getAgility, grow(pet.getAgility(), next.getBonusAgility()))
                .set(Pet::getCharm, grow(pet.getCharm(), next.getBonusCharm()))
                .setSql("version = version + 1")
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getEvolutionStage, fromStage)
                .eq(Pet::getVersion, pet.getVersion() != null ? pet.getVersion() : 0));
        if (updated == 0) {
            // B02：版本/阶段冲突必须显式失败，禁止静默丢更新
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "宠物状态被并发修改，请稍后重试");
        }
        pet.setEvolutionStage(stageTo);
        pet.setMaxHp(newMaxHp);
        pet.setHp(newHp);
        grantUnlockSkin(pet, next.getUnlockSkinCode());
    }

    /** 扣款快照（R28：冻结 fromStage/configVersion，恢复任务与对账可核验原意图） */
    private String snapshot(Pet pet, PetEvolutionConfig next, int fromStage, int cost) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("fromStage", fromStage);
        snapshot.put("stageTo", next.getStageTo());
        snapshot.put("configVersion", next.getUpdatedAt() == null ? "0"
                : next.getUpdatedAt().toString());
        snapshot.put("bonusMaxHp", orZero(next.getBonusMaxHp()));
        snapshot.put("bonusStrength", orZero(next.getBonusStrength()));
        snapshot.put("bonusIntelligence", orZero(next.getBonusIntelligence()));
        snapshot.put("bonusAgility", orZero(next.getBonusAgility()));
        snapshot.put("bonusCharm", orZero(next.getBonusCharm()));
        snapshot.put("unlockSkinCode", next.getUnlockSkinCode());
        snapshot.put("cost", cost);
        return PetJsonUtils.toJson(snapshot);
    }

    private PetEvolutionVO buildStatus(Pet pet, Long balance) {
        int stage = currentStage(pet);
        List<PetEvolutionConfig> configs = enabledConfigs();
        int maxStage = configs.stream()
                .mapToInt(config -> orZero(config.getStageTo()))
                .max().orElse(0);
        PetEvolutionConfig next = nextConfig(configs, stage);
        if (next == null) {
            return new PetEvolutionVO(stage, maxStage, null, null, null, null, null,
                    null, null, null, null, null, null, null, false, "已达到最高进化阶段");
        }
        int requiredLevel = next.getRequiredLevel() != null ? next.getRequiredLevel() : 1;
        int cost = orZero(next.getCostStarlight());
        boolean levelOk = pet.getLevel() >= requiredLevel;
        boolean balanceOk = balance == null || balance >= cost;
        boolean canEvolve = levelOk && balanceOk;
        String lockReason = null;
        if (!levelOk) {
            lockReason = "需要 Lv." + requiredLevel;
        } else if (!balanceOk) {
            lockReason = "星光不足（需要 " + cost + "）";
        }
        return new PetEvolutionVO(stage, maxStage, next.getCode(), next.getName(), next.getDescription(),
                next.getRequiredLevel(), cost, next.getBonusMaxHp(), next.getBonusStrength(),
                next.getBonusIntelligence(), next.getBonusAgility(), next.getBonusCharm(),
                next.getUnlockSkinCode(), next.getIcon(), canEvolve, lockReason);
    }

    private List<PetEvolutionConfig> enabledConfigs() {
        return evolutionConfigMapper.selectList(new LambdaQueryWrapper<PetEvolutionConfig>()
                .eq(PetEvolutionConfig::getEnabled, true)
                .orderByAsc(PetEvolutionConfig::getSort));
    }

    private PetEvolutionConfig nextConfig(List<PetEvolutionConfig> configs, int stage) {
        return configs.stream()
                .filter(config -> orZero(config.getStageFrom()) == stage)
                .findFirst()
                .orElse(null);
    }

    private int currentStage(Pet pet) {
        return pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0;
    }

    /** 进化解锁皮肤：直接入包（已拥有则跳过，不报错） */
    private void grantUnlockSkin(Pet pet, String skinCode) {
        if (skinCode == null || skinCode.isBlank()) {
            return;
        }
        PetInventory skin = new PetInventory();
        skin.setPetId(pet.getId());
        skin.setUserId(pet.getUserId());
        skin.setItemType(PetItemType.SKIN.name());
        skin.setItemCode(skinCode);
        skin.setQuantity(1);
        skin.setEquipped(false);
        skin.setAcquiredAt(petClock.nowUtc());
        try {
            inventoryMapper.insert(skin);
        } catch (DuplicateKeyException e) {
            log.debug("进化解锁皮肤已拥有，跳过入包: petId={}, skin={}", pet.getId(), skinCode);
        }
    }

    private void recordEvolutionActivity(Pet pet) {
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(pet.getUserId());
        activity.setActivityType(PetActivityType.EVOLVE.name());
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        var now = petClock.nowUtc();
        activity.setStartedAt(now);
        activity.setFinishedAt(now);
        activity.setClaimedAt(now);
        activity.setResult("{\"evolutionStage\":" + currentStage(pet) + "}");
        activityMapper.insert(activity);
    }

    private int grow(Integer value, Integer bonus) {
        return Math.min(ATTRIBUTE_MAX, orZero(value) + orZero(bonus));
    }

    private int orZero(Integer value) {
        return value != null ? value : 0;
    }

    /** 余额查询：展示型数据 Fail-Open（null=不参与"星光是否足够"判定） */
    private Long balanceQuietly(Long userId) {
        try {
            return economyService.balanceOf(userId);
        } catch (Exception e) {
            log.warn("星光余额查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }
}
