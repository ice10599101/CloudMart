package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonPass;
import com.cloudmart.pet.entity.PetSeasonPassClaim;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonPassClaimMapper;
import com.cloudmart.pet.repository.PetSeasonPassMapper;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.service.PetSeasonPassService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetSeasonPassVO;
import com.cloudmart.pet.wallet.PetRewardApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 赛季通行证（R05 重写）。
 *
 * <p>领取裁决：uk(season,user,tier) 逐档领取事实——并发领不同档互不覆盖、同档
 * 重复只交付一次；交付经奖励应用服务（钱包冻结/维护检查 + 币入账业务键防重），
 * 失败留 CLAIM_PENDING 由 {@link #settlePendingClaims()} 定时补发，绝不把
 * PENDING 展示为 CLAIMED。经验按事实表 (season,user,source,sourceBizId) 去重累积。
 * 档位阈值可配置（默认沿用 10+(n-1)*5 迁移草稿口径），最终档皮肤承诺以实际配置为准。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetSeasonPassServiceImpl implements PetSeasonPassService {

    private static final int TIER_COUNT = 10;
    private static final int BASE_TIER_EXP = 10;
    private static final int TIER_EXP_STEP = 5;
    private static final String RULE_VERSION = "V1";

    /** T18：可重试（瞬态）错误码——维护中/处理中/临时不可用/忙碌；其余业务错误按不可重试处置 */
    private static final Set<String> RETRYABLE_CODES = Set.of(
            PetErrorCodes.PET_WALLET_MAINTENANCE,
            PetErrorCodes.PET_REQUEST_IN_PROGRESS,
            PetErrorCodes.PET_TEMPORARILY_UNAVAILABLE,
            PetErrorCodes.PET_USER_BUSY);

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonPassMapper passMapper;
    private final PetSeasonPassClaimMapper claimMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetRewardApplicationService rewardApplicationService;
    private final PetService petService;
    private final PetProperties properties;
    /** T17：经验原子累计器（独立 Bean，REQUIRES_NEW 保证事实与累计同事务） */
    private final PetSeasonPassExpAccumulator expAccumulator;
    /** T18：补发积压/重试率/终止数指标（Prometheus 告警口径） */
    private final com.cloudmart.pet.config.PetSeasonPassClaimMetrics claimMetrics;

    /** T18：本实例补发租约持有者 ID（多实例并发领取 fenced by tryLease CAS） */
    private final String leaseOwner = UUID.randomUUID().toString();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最终档赛季皮肤码（须存在于皮肤配置；空串=不发皮肤，奖励描述不承诺皮肤） */
    @Value("${pet.season-pass.final-tier-skin-code:}")
    String finalTierSkinCode;

    /** R05：档位经验阈值配置（逗号分隔，长度=档位数；空=沿用迁移草稿公式） */
    @Value("${pet.season-pass.tier-exp-thresholds:}")
    private String tierExpThresholds;

    // ==================== 查询 ====================

    @Override
    public PetSeasonPassVO myPass(Long userId) {
        PetSeason season = activeSeason();
        if (season == null) {
            return emptyPass();
        }
        return seasonPassOf(season, userId);
    }

    @Override
    public PetSeasonPassVO seasonPass(Long userId, Long seasonId) {
        PetSeason season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "赛季不存在");
        }
        return seasonPassOf(season, userId);
    }

    @Override
    public List<PetSeason> mySeasons(Long userId) {
        List<Long> seasonIds = passMapper.selectList(new LambdaQueryWrapper<PetSeasonPass>()
                        .eq(PetSeasonPass::getUserId, userId)
                        .select(PetSeasonPass::getSeasonId))
                .stream()
                .map(PetSeasonPass::getSeasonId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (seasonIds.isEmpty()) {
            return List.of();
        }
        return seasonMapper.selectList(new LambdaQueryWrapper<PetSeason>()
                .in(PetSeason::getId, seasonIds)
                .orderByDesc(PetSeason::getEndsAt));
    }

    // ==================== 领取 ====================

    @Override
    public PetSeasonPassVO claimTier(Long userId, Long seasonId, int tier, String requestKey) {
        // R19：能力开关——关闭时拒绝新领取；通行证进度查询不拦
        if (!properties.getFeatureSwitches().isSeasonPass()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "赛季通行证暂未开放");
        }
        if (tier < 1 || tier > TIER_COUNT) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "档位非法");
        }
        PetSeason season = seasonId != null ? seasonMapper.selectById(seasonId) : activeSeason();
        if (season == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "当前没有进行中的赛季");
        }
        checkClaimWindow(season);
        PetSeasonPass pass = requirePass(season.getId(), userId);
        int required = requiredExp(tier);
        if (pass.getPassExp() < required) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "通行证经验不足（需要 " + required + "）");
        }

        // 权威裁决：uk(season,user,tier)——并发领不同档互不覆盖，同档重复命中已存在行
        PetSeasonPassClaim claim = new PetSeasonPassClaim();
        claim.setSeasonId(season.getId());
        claim.setUserId(userId);
        claim.setTier(tier);
        claim.setStatus(PetSeasonPassClaim.STATUS_CLAIM_PENDING);
        // T16：领取时冻结奖励快照（币/皮肤/规则版本/目标宠物），补发只读快照
        claim.setRewardSnapshot(rewardSnapshot(tier, resolveTargetPet(userId, tier)));
        try {
            claimMapper.insert(claim);
        } catch (DuplicateKeyException duplicate) {
            PetSeasonPassClaim existing = loadClaim(season.getId(), userId, tier);
            if (existing == null || PetSeasonPassClaim.STATUS_SETTLED.equals(existing.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该档位奖励已领取");
            }
            // 在途交付（上一次未完成）：返回当前视图（PENDING 不展示为已领取）
            log.info("通行证档位在途交付返回原状态: season={}, user={}, tier={}",
                    season.getId(), userId, tier);
            return seasonPassOf(season, userId);
        }

        // 交付：奖励应用服务（uk 防重 + 钱包冻结/维护检查）；失败留 CLAIM_PENDING 可恢复
        try {
            deliver(claim);
        } catch (Exception e) {
            log.warn("通行证档位奖励交付失败（待补发）: season={}, user={}, tier={}, err={}",
                    season.getId(), userId, tier, e.getMessage());
            claimMetrics.recordRetry();
            // 领取事实保持 CLAIM_PENDING（已提交），由定时补发收敛；不虚报已领取
            return seasonPassOf(season, userId);
        }

        claim.setStatus(PetSeasonPassClaim.STATUS_SETTLED);
        claim.setSettledAt(LocalDateTime.now());
        claimMapper.updateById(claim);
        syncClaimedProjection(season.getId(), userId);
        log.info("赛季通行证奖励已交付: season={}, user={}, tier={}, key={}",
                season.getId(), userId, tier, requestKey);
        return seasonPassOf(season, userId);
    }

    // ==================== 经验事实 ====================

    @Override
    public void addExp(Long userId, int exp, String source, String sourceBizId) {
        if (exp <= 0) {
            return;
        }
        if (source == null || source.isBlank() || sourceBizId == null || sourceBizId.isBlank()) {
            // 调用方契约：经验必须绑定来源事实 ID（R05 去重），缺参属编程错误
            throw new IllegalArgumentException("通行证经验必须绑定来源事实: source=" + source
                    + ", sourceBizId=" + sourceBizId);
        }
        try {
            // T17：事实插入与累加在独立 Bean 的 REQUIRES_NEW 事务内原子完成；
            // 失败整体回滚（事实也不落库），重放重新插入并累加，不再出现"事实在、经验丢"。
            expAccumulator.accumulate(userId, exp, source, sourceBizId);
        } catch (Exception e) {
            log.warn("赛季通行证经验累积失败（已原子回滚，待重放，不阻断任务主流程）: userId={}, err={}",
                    userId, e.getMessage());
        }
    }

    // ==================== 校验 ====================

    @Override
    public PetSeasonPassService.ExpReconciliation reconcileExp(Long seasonId, Long userId) {
        long factSum = expAccumulator.expSumFromFacts(seasonId, userId);
        PetSeasonPass pass = passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                .eq(PetSeasonPass::getSeasonId, seasonId)
                .eq(PetSeasonPass::getUserId, userId));
        int projected = pass == null || pass.getPassExp() == null ? 0 : pass.getPassExp();
        if (factSum != projected) {
            log.warn("通行证经验投影漂移（需运维核实按事实重建，禁止直接加数）: season={}, user={}, factSum={}, projected={}",
                    seasonId, userId, factSum, projected);
        }
        return new PetSeasonPassService.ExpReconciliation(factSum, projected);
    }

    @Override
    public PetSeasonPassClaim retryClaim(Long claimId, String reason) {
        PetSeasonPassClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "领取事实不存在");
        }
        if (PetSeasonPassClaim.STATUS_SETTLED.equals(claim.getStatus())) {
            return claim; // 已交付，幂等
        }
        // 重放原事实（领取时快照，不新造奖励）：重置为立即待补发，释放租约与退避
        int updated = claimMapper.update(null, new LambdaUpdateWrapper<PetSeasonPassClaim>()
                .set(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_CLAIM_PENDING)
                .set(PetSeasonPassClaim::getNextRetryAt, null)
                .set(PetSeasonPassClaim::getLeaseOwner, null)
                .set(PetSeasonPassClaim::getLeaseUntil, null)
                .eq(PetSeasonPassClaim::getId, claimId)
                .in(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_NEEDS_ATTENTION,
                        PetSeasonPassClaim.STATUS_CLAIM_PENDING));
        if (updated == 1) {
            claimMetrics.recordManualRetry();
            log.info("[OPS] 通行证奖励人工重试: claimId={}, season={}, user={}, tier={}, reason={}",
                    claimId, claim.getSeasonId(), claim.getUserId(), claim.getTier(), reason);
        }
        return claimMapper.selectById(claimId);
    }

    // ==================== 定时补发 ====================

    @Override
    @Scheduled(fixedDelayString = "${pet.season-pass.reward-sweep-delay-ms:15000}")
    public int settlePendingClaims() {
        PetProperties.SeasonPass cfg = properties.getSeasonPass();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // T18 公平扫描：只取「已到期且可执行」，失败行退避后不再占头部
        List<PetSeasonPassClaim> due = claimMapper.selectDuePending(now, cfg.getSweepBatchSize());
        int settled = 0;
        for (PetSeasonPassClaim claim : due) {
            LocalDateTime leaseUntil = now.plusSeconds(cfg.getLeaseSeconds());
            // 租约 CAS：多实例并发只一个胜者，交付由奖励应用服务 uk 幂等兜底
            if (claimMapper.tryLease(claim.getId(), leaseOwner, leaseUntil, now) != 1) {
                continue;
            }
            try {
                deliver(claim);
                markSettled(claim);
                claimMetrics.recordSettled();
                settled++;
            } catch (Exception e) {
                boolean terminal = recordFailure(claim, e, now);
                if (terminal) {
                    claimMetrics.recordTerminal();
                } else {
                    claimMetrics.recordRetry();
                }
            }
        }
        return settled;
    }

    /** 交付成功后置 SETTLED（fence 内 CAS PENDING→SETTLED，释放租约）并同步投影 */
    private void markSettled(PetSeasonPassClaim claim) {
        int updated = claimMapper.update(null, new LambdaUpdateWrapper<PetSeasonPassClaim>()
                .set(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_SETTLED)
                .set(PetSeasonPassClaim::getSettledAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(PetSeasonPassClaim::getLeaseOwner, null)
                .set(PetSeasonPassClaim::getLeaseUntil, null)
                .eq(PetSeasonPassClaim::getId, claim.getId())
                .eq(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_CLAIM_PENDING));
        if (updated == 1) {
            syncClaimedProjection(claim.getSeasonId(), claim.getUserId());
        }
    }

    /** 失败记录：指数退避 + 抖动；达上限或不可重试 → NEEDS_ATTENTION（可见可处置）；@return 是否终止 */
    private boolean recordFailure(PetSeasonPassClaim claim, Exception cause, LocalDateTime now) {
        PetProperties.SeasonPass cfg = properties.getSeasonPass();
        int attempts = (claim.getAttempts() == null ? 0 : claim.getAttempts()) + 1;
        String errorCode = classifyError(cause);
        boolean terminal = !isRetryable(cause) || attempts >= cfg.getMaxAttempts();
        LambdaUpdateWrapper<PetSeasonPassClaim> update = new LambdaUpdateWrapper<PetSeasonPassClaim>()
                .set(PetSeasonPassClaim::getAttempts, attempts)
                .set(PetSeasonPassClaim::getLastErrorCode, errorCode)
                .set(PetSeasonPassClaim::getLeaseOwner, null)
                .set(PetSeasonPassClaim::getLeaseUntil, null)
                .set(PetSeasonPassClaim::getLastAttemptAt, now)
                .eq(PetSeasonPassClaim::getId, claim.getId())
                .eq(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_CLAIM_PENDING);
        if (terminal) {
            update.set(PetSeasonPassClaim::getStatus, PetSeasonPassClaim.STATUS_NEEDS_ATTENTION);
        } else {
            update.set(PetSeasonPassClaim::getNextRetryAt, now.plusSeconds(backoffSeconds(attempts, cfg)));
        }
        claimMapper.update(null, update);
        log.warn("通行证奖励补发失败: claimId={}, season={}, user={}, tier={}, attempts={}, code={}, terminal={}, err={}",
                claim.getId(), claim.getSeasonId(), claim.getUserId(), claim.getTier(),
                attempts, errorCode, terminal, cause.getMessage());
        return terminal;
    }

    /** 指数退避 + 抖动（±25%），封顶 maxRetrySeconds */
    private long backoffSeconds(int attempts, PetProperties.SeasonPass cfg) {
        long base = cfg.getBaseRetrySeconds();
        long exp = base * (1L << Math.min(Math.max(attempts - 1, 0), 20));
        long capped = Math.min(exp, cfg.getMaxRetrySeconds());
        return capped + (long) (capped * 0.25 * Math.random());
    }

    /** 可重试错误：瞬态（维护中/处理中/临时不可用/忙碌）与非业务异常（DB/网络）；其余为不可重试 */
    private boolean isRetryable(Throwable cause) {
        if (!(cause instanceof BusinessException be)) {
            return true;
        }
        String code = be.getCode() == null ? "" : be.getCode();
        return RETRYABLE_CODES.contains(code);
    }

    private String classifyError(Throwable cause) {
        if (cause instanceof BusinessException be) {
            return be.getCode() == null ? "PET_BUSINESS_ERROR" : be.getCode();
        }
        return "PET_DELIVERY_UNAVAILABLE";
    }

    // ==================== 内部 ====================

    /**
     * T16：交付严格反序列化领取时的奖励快照（币/皮肤/规则版本/目标宠物），
     * 不再读取当前 finalTierSkinCode/tierCoins/RULE_VERSION——赛季配置或主宠
     * 在领取后变化不得改变已承诺的奖励。
     */
    private void deliver(PetSeasonPassClaim claim) {
        RewardSnapshot snap = parseSnapshot(claim.getRewardSnapshot());
        String bizId = claim.getSeasonId() + ":" + claim.getTier();
        rewardApplicationService.claim(
                new PetRewardApplicationService.RewardClaimCommand(
                        claim.getUserId(), null, "PASS_TIER", bizId, "MAIN", null),
                command -> {
                    if (snap.skin() != null && !snap.skin().isBlank()) {
                        grantSkin(claim.getUserId(), snap.skin(), snap.targetPetId());
                    }
                    return new PetRewardApplicationService.ResolvedReward(
                            snap.coins(), "", List.of(), snap.ruleVersion());
                });
    }

    /** 最终档皮肤：按快照目标宠物直插 inventory（重复幂等：exists 检查；随 claim 事务回滚） */
    private void grantSkin(Long userId, String skinCode, Long targetPetId) {
        Pet pet = targetPetId != null
                ? petService.requireOwnedPet(userId, targetPetId)
                : petOwned(userId);
        long exists = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, "SKIN")
                .eq(PetInventory::getItemCode, skinCode));
        if (exists > 0) {
            return;
        }
        PetInventory item = new PetInventory();
        item.setPetId(pet.getId());
        item.setUserId(userId);
        item.setItemType("SKIN");
        item.setItemCode(skinCode);
        item.setQuantity(1);
        item.setEquipped(false);
        item.setAcquiredAt(LocalDateTime.now());
        inventoryMapper.insert(item);
        log.info("赛季通行证皮肤已发放: petId={}, skin={}", pet.getId(), skinCode);
    }

    private Pet petOwned(Long userId) {
        return petService.requireOwnedPet(userId);
    }

    /** 最终档皮肤的承诺目标宠物（领取时冻结；无主宠时留空由补发时兜底） */
    private Long resolveTargetPet(Long userId, int tier) {
        if (tier != TIER_COUNT || !skinConfigured()) {
            return null;
        }
        try {
            return petService.requireOwnedPet(userId).getId();
        } catch (Exception noPet) {
            return null;
        }
    }

    private boolean skinConfigured() {
        return finalTierSkinCode != null && !finalTierSkinCode.isBlank();
    }

    /** 投影同步（claimed_tiers JSON 降级为展示投影；权威在 claim 表） */
    private void syncClaimedProjection(Long seasonId, Long userId) {
        try {
            PetSeasonPass pass = passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                    .eq(PetSeasonPass::getSeasonId, seasonId)
                    .eq(PetSeasonPass::getUserId, userId));
            if (pass == null) {
                return;
            }
            List<Integer> settled = loadClaims(seasonId, userId).stream()
                    .filter(c -> PetSeasonPassClaim.STATUS_SETTLED.equals(c.getStatus()))
                    .map(PetSeasonPassClaim::getTier)
                    .sorted()
                    .toList();
            for (int attempt = 0; attempt < 3; attempt++) {
                int updated = passMapper.update(null, new LambdaUpdateWrapper<PetSeasonPass>()
                        .eq(PetSeasonPass::getId, pass.getId())
                        .eq(PetSeasonPass::getVersion, pass.getVersion() == null ? 0 : pass.getVersion())
                        .set(PetSeasonPass::getClaimedTiers, toJson(settled))
                        .setSql("version = version + 1"));
                if (updated == 1) {
                    return;
                }
                pass = passMapper.selectById(pass.getId());
                if (pass == null) {
                    return;
                }
            }
            log.warn("通行证投影同步放弃（展示口径仍以 claim 表为准）: season={}, user={}", seasonId, userId);
        } catch (Exception e) {
            log.warn("通行证投影同步异常（不影响权威事实）: season={}, user={}, err={}", seasonId, userId, e.getMessage());
        }
    }

    private PetSeasonPassClaim loadClaim(Long seasonId, Long userId, int tier) {
        return claimMapper.selectOne(new LambdaQueryWrapper<PetSeasonPassClaim>()
                .eq(PetSeasonPassClaim::getSeasonId, seasonId)
                .eq(PetSeasonPassClaim::getUserId, userId)
                .eq(PetSeasonPassClaim::getTier, tier)
                .last("LIMIT 1"));
    }

    private List<PetSeasonPassClaim> loadClaims(Long seasonId, Long userId) {
        return claimMapper.selectList(new LambdaQueryWrapper<PetSeasonPassClaim>()
                .eq(PetSeasonPassClaim::getSeasonId, seasonId)
                .eq(PetSeasonPassClaim::getUserId, userId));
    }

    /** 进行中赛季：ACTIVE 且在起止窗口内（R05：不再只看 status） */
    private PetSeason activeSeason() {
        LocalDateTime now = LocalDateTime.now();
        return seasonMapper.selectOne(new LambdaQueryWrapper<PetSeason>()
                .eq(PetSeason::getStatus, "ACTIVE")
                .and(w -> w.isNull(PetSeason::getStartsAt).or().le(PetSeason::getStartsAt, now))
                .and(w -> w.isNull(PetSeason::getEndsAt).or().ge(PetSeason::getEndsAt, now))
                .last("LIMIT 1"));
    }

    /** 领奖窗口：claim_until（缺省随赛季结束）之前可领取/补领 */
    private void checkClaimWindow(PetSeason season) {
        LocalDateTime deadline = season.getClaimUntil() != null ? season.getClaimUntil() : season.getEndsAt();
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "本赛季领奖已截止");
        }
    }

    /** 懒创建通行证行（uk 并发兜底后回查） */
    private PetSeasonPass requirePass(Long seasonId, Long userId) {
        PetSeasonPass pass = passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                .eq(PetSeasonPass::getSeasonId, seasonId)
                .eq(PetSeasonPass::getUserId, userId));
        if (pass != null) {
            return pass;
        }
        PetSeasonPass created = new PetSeasonPass();
        created.setSeasonId(seasonId);
        created.setUserId(userId);
        created.setPassExp(0);
        created.setClaimedTiers("[]");
        created.setVersion(0);
        try {
            passMapper.insert(created);
            return created;
        } catch (DuplicateKeyException concurrent) {
            return passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                    .eq(PetSeasonPass::getSeasonId, seasonId)
                    .eq(PetSeasonPass::getUserId, userId));
        }
    }

    private PetSeasonPassVO seasonPassOf(PetSeason season, Long userId) {
        PetSeasonPass pass = requirePass(season.getId(), userId);
        Map<Integer, String> statusByTier = new HashMap<>();
        for (PetSeasonPassClaim claim : loadClaims(season.getId(), userId)) {
            statusByTier.merge(claim.getTier(), claim.getStatus(),
                    (a, b) -> PetSeasonPassClaim.STATUS_SETTLED.equals(a) ? a : b);
        }
        List<Integer> settledTiers = statusByTier.entrySet().stream()
                .filter(e -> PetSeasonPassClaim.STATUS_SETTLED.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<PetSeasonPassVO.Tier> tiers = new ArrayList<>();
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            int required = requiredExp(tier);
            String claimStatus = statusByTier.get(tier);
            tiers.add(new PetSeasonPassVO.Tier(
                    tier, required, pass.getPassExp() >= required,
                    PetSeasonPassClaim.STATUS_SETTLED.equals(claimStatus),
                    tierRewardDesc(tier), claimStatus));
        }
        return new PetSeasonPassVO(season.getId(), season.getName(), pass.getPassExp(),
                settledTiers, tiers, season.getClaimUntil(), season.getEndsAt());
    }

    private PetSeasonPassVO emptyPass() {
        List<PetSeasonPassVO.Tier> tiers = new ArrayList<>();
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            tiers.add(new PetSeasonPassVO.Tier(tier, requiredExp(tier), false, false,
                    tierRewardDesc(tier), null));
        }
        return new PetSeasonPassVO(null, null, 0, List.of(), tiers, null, null);
    }

    /** 档位阈值：配置优先（逗号分隔，逐档取值），缺省沿用迁移草稿公式 */
    int requiredExp(int tier) {
        if (tierExpThresholds != null && !tierExpThresholds.isBlank()) {
            String[] parts = tierExpThresholds.split(",");
            if (tier <= parts.length) {
                try {
                    return Integer.parseInt(parts[tier - 1].strip());
                } catch (NumberFormatException ignored) {
                    // 配置段非法回退公式（发布校验拦截非法配置）
                }
            }
        }
        return BASE_TIER_EXP + (tier - 1) * TIER_EXP_STEP;
    }

    private String tierRewardDesc(int tier) {
        long coins = tierCoins(tier);
        return tier == TIER_COUNT
                ? (skinConfigured() ? coins + " 宠物币 + 赛季限定皮肤" : coins + " 宠物币")
                : coins + " 宠物币";
    }

    private long tierCoins(int tier) {
        return 20L * tier;
    }

    private String rewardSnapshot(int tier, Long targetPetId) {
        try {
            Map<String, Object> snap = new HashMap<>();
            snap.put("tier", tier);
            snap.put("coins", tierCoins(tier));
            snap.put("skin", tier == TIER_COUNT && skinConfigured() ? finalTierSkinCode.trim() : "");
            snap.put("ruleVersion", RULE_VERSION);
            if (targetPetId != null) {
                snap.put("targetPetId", targetPetId);
            }
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 领取奖励快照（T16）：补发只读此快照，不读当前配置 */
    private record RewardSnapshot(long coins, String skin, String ruleVersion, Long targetPetId) {}

    private RewardSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return new RewardSnapshot(0, "", RULE_VERSION, null);
        }
        try {
            var node = objectMapper.readTree(json);
            long coins = node.path("coins").asLong(0);
            String skin = node.path("skin").asText("");
            String ruleVersion = node.hasNonNull("ruleVersion") ? node.path("ruleVersion").asText() : RULE_VERSION;
            Long targetPetId = node.hasNonNull("targetPetId") ? node.path("targetPetId").asLong() : null;
            return new RewardSnapshot(coins, skin, ruleVersion, targetPetId);
        } catch (Exception e) {
            return new RewardSnapshot(0, "", RULE_VERSION, null);
        }
    }

    private String toJson(List<Integer> tiers) {
        try {
            return objectMapper.writeValueAsString(tiers);
        } catch (Exception e) {
            return "[]";
        }
    }
}
