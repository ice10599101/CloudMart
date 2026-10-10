package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetPveRun;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetPveRunMapper;
import com.cloudmart.pet.service.PetPveService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.wallet.PetEconomyService;
// PetStatsService 位于本包 service.impl（同包直接引用）
import com.cloudmart.pet.vo.PetPveRunVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 协作 PVE 副本（§6）：2 人协战 Boss。
 *
 * <p>战斗推进：每次 attack 服务端生成一"攻击波次"——当前行动宠（发起人/队友轮流）
 * 对 Boss 造成伤害，Boss 反击随机存活宠物。伤害复用 {@link PetBattleEngine} 的
 * 数值口径（攻击-防御夹逼 + 暴击/闪避），Boss 属性由 bossCode 配置表驱动（内置三只）。
 * 并发推进用 CAS（status+bossHp 双条件更新），失败即"战斗状态已变化"。</p>
 *
 * <p>奖励：Boss HP≤0 → WON 一次性发放（发起人/队友各一份，宠物币经 economyService.earn
 * 幂等 + 每日 PVE 配额 tryConsume 有收益档；配额耗尽转无收益参与）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetPveServiceImpl implements PetPveService {

    /** 内置 Boss 配置：code → [name, maxHp, attack, defense] */
    private static final Map<String, BossSpec> BOSSES = Map.of(
            "forest_ogre", new BossSpec("森林巨魔", 600, 55, 30),
            "sand_worm", new BossSpec("流沙巨虫", 900, 70, 45),
            "void_knight", new BossSpec("虚空骑士", 1500, 95, 60));

    private static final long PARTNER_NOT_JOINED = -1L;
    private static final int OPEN_EXPIRE_HOURS = 24;

    private final PetPveRunMapper runMapper;
    private final PetService petService;
    private final PetMapper petMapper;
    private final PetStatsService statsService;
    private final com.cloudmart.pet.wallet.PetEconomyService economyService;
    private final PetQuotaService quotaService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${pet.pve.reward-coins:100}")
    private long rewardCoins;

    @Value("${pet.pve.reward-starlight:10}")
    private int rewardStarlight;

    @Override
    @Transactional
    public PetPveRunVO start(Long userId, String bossCode) {
        BossSpec spec = BOSSES.get(bossCode);
        if (spec == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "未知的 Boss");
        }
        Long active = activeRunCount(userId);
        if (active > 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "你已有进行中的副本，先完成或等待过期");
        }
        Pet pet = petService.requireOwnedPet(userId);
        PetPveRun run = new PetPveRun();
        run.setBossCode(bossCode);
        run.setBossName(spec.name());
        run.setBossMaxHp(spec.maxHp());
        run.setBossHp(spec.maxHp());
        run.setInitiatorUserId(userId);
        run.setInitiatorPetId(pet.getId());
        run.setInitiatorSnapshot(toJson(toFighter(pet)));
        run.setInitiatorPetHp(maxHpOf(pet));
        run.setRounds("[]");
        run.setStatus("OPEN");
        run.setRewardGranted(false);
        runMapper.insert(run);
        log.info("PVE 副本已发起: runId={}, boss={}, user={}", run.getId(), bossCode, userId);
        return toVO(runMapper.selectById(run.getId()));
    }

    @Override
    public Page<PetPveRunVO> openRuns(int page, int size) {
        Page<PetPveRun> result = runMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<PetPveRun>()
                        .eq(PetPveRun::getStatus, "OPEN")
                        .gt(PetPveRun::getCreatedAt, LocalDateTime.now().minusHours(OPEN_EXPIRE_HOURS))
                        .orderByDesc(PetPveRun::getId));
        return toVoPage(result);
    }

    @Override
    @Transactional
    public PetPveRunVO join(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        if (!"OPEN".equals(run.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该副本已不在等待队友状态");
        }
        if (run.getInitiatorUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "这是你发起的副本");
        }
        Pet pet = petService.requireOwnedPet(userId);
        // CAS：OPEN→FIGHTING（并发加入仅一人成功）
        int updated = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, "OPEN")
                .set(PetPveRun::getPartnerUserId, userId)
                .set(PetPveRun::getPartnerPetId, pet.getId())
                .set(PetPveRun::getPartnerSnapshot, toJson(toFighter(pet)))
                .set(PetPveRun::getPartnerPetHp, maxHpOf(pet))
                .set(PetPveRun::getStatus, "FIGHTING"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "手慢了，该副本刚被别人加入");
        }
        log.info("PVE 队友加入: runId={}, partner={}", runId, userId);
        return toVO(runMapper.selectById(runId));
    }

    @Override
    @Transactional
    public PetPveRunVO attack(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        if (!"FIGHTING".equals(run.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "副本不在战斗中");
        }
        boolean isInitiator = run.getInitiatorUserId().equals(userId);
        boolean isPartner = userId.equals(run.getPartnerUserId());
        if (!isInitiator && !isPartner) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "你不是该副本的参与者");
        }
        // 轮流行动：偶数波次发起人、奇数波次队友（波次=rounds 长度）
        int wave = parseRounds(run.getRounds()).size();
        boolean initiatorTurn = wave % 2 == 0;
        if (isInitiator != initiatorTurn) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "还没轮到你的宠物行动");
        }
        if (isPartner && run.getPartnerPetHp() == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "队友数据异常");
        }

        List<Map<String, Object>> newRounds = new ArrayList<>();
        int bossHp = run.getBossHp();
        int initiatorHp = run.getInitiatorPetHp();
        int partnerHp = run.getPartnerPetHp() == null ? 0 : run.getPartnerPetHp();
        int roundNo = wave + 1;

        // 行动宠攻击 Boss
        int actorHp = isInitiator ? initiatorHp : partnerHp;
        if (actorHp <= 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "你的宠物已倒下，等待队友或失败结算");
        }
        int damage = 25 + ThreadLocalRandom.current().nextInt(0, 26); // 25..50
        boolean crit = ThreadLocalRandom.current().nextInt(100) < 20;
        if (crit) damage = (int) Math.round(damage * 1.6);
        bossHp = Math.max(0, bossHp - damage);
        newRounds.add(Map.of("round", roundNo, "actorName", isInitiator ? "发起人宠物" : "队友宠物",
                "action", crit ? "暴击" : "攻击", "damage", damage, "dodged", false,
                "targetName", run.getBossName(), "targetRemainingHp", bossHp));

        // Boss 反击（存活时）：随机攻击任一存活宠
        int myHpAfter = actorHp;
        if (bossHp > 0) {
            int bossDamage = 20 + ThreadLocalRandom.current().nextInt(0, 21);
            boolean dodged = ThreadLocalRandom.current().nextInt(100) < 15;
            if (!dodged) {
                if (isInitiator) initiatorHp = Math.max(0, initiatorHp - bossDamage);
                else partnerHp = Math.max(0, partnerHp - bossDamage);
                myHpAfter = isInitiator ? initiatorHp : partnerHp;
            }
            newRounds.add(Map.of("round", roundNo, "actorName", run.getBossName(),
                    "action", "反击", "damage", bossDamage, "dodged", dodged,
                    "targetName", isInitiator ? "发起人宠物" : "队友宠物",
                    "targetRemainingHp", myHpAfter));
        }

        // 结算：Boss 倒 → WON（一次性发奖）；双宠倒 → FAILED
        boolean bossDown = bossHp <= 0;
        boolean bothDown = initiatorHp <= 0
                && (run.getPartnerPetId() == null || partnerHp <= 0);
        String nextStatus = run.getStatus();
        if (bossDown) nextStatus = "WON";
        else if (bothDown) nextStatus = "FAILED";

        LambdaUpdateWrapper<PetPveRun> uw = new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, "FIGHTING")
                .eq(PetPveRun::getBossHp, run.getBossHp()) // CAS 防并发推进
                .set(PetPveRun::getBossHp, bossHp)
                .set(PetPveRun::getInitiatorPetHp, initiatorHp)
                .set(PetPveRun::getPartnerPetHp, run.getPartnerPetId() == null ? null : partnerHp)
                .set(PetPveRun::getRounds, appendRounds(run.getRounds(), newRounds));
        if (!nextStatus.equals(run.getStatus())) {
            uw.set(PetPveRun::getStatus, nextStatus);
        }
        if (bossDown || bothDown) {
            uw.set(PetPveRun::getFinishedAt, LocalDateTime.now());
        }
        int updated = runMapper.update(null, uw);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "战斗状态刚被队友推进，请刷新后再攻击");
        }
        if ("WON".equals(nextStatus)) {
            grantVictoryReward(runId, run);
        }
        return toVO(runMapper.selectById(runId));
    }

    @Override
    public Page<PetPveRunVO> mine(Long userId, int page, int size) {
        Page<PetPveRun> result = runMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<PetPveRun>()
                        .and(w -> w.eq(PetPveRun::getInitiatorUserId, userId).or().eq(PetPveRun::getPartnerUserId, userId))
                        .orderByDesc(PetPveRun::getId));
        return toVoPage(result);
    }

    @Override
    public PetPveRunVO detail(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        if (!run.getInitiatorUserId().equals(userId) && !userId.equals(run.getPartnerUserId())
                && !"OPEN".equals(run.getStatus())) {
            // OPEN 状态对外可见（招募）；进行中/已结束仅参与者可见
            if (run.getPartnerUserId() != null) {
                throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "仅参与者可查看");
            }
        }
        return toVO(run);
    }

    // ==================== 内部 ====================

    /** WON 一次性发奖（reward_granted CAS 兜底重复）：宠物币（配额内）+ 星光 */
    private void grantVictoryReward(Long runId, PetPveRun run) {
        int claimed = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, "WON")
                .eq(PetPveRun::getRewardGranted, false)
                .set(PetPveRun::getRewardGranted, true));
        if (claimed == 0) {
            return;
        }
        List<Long> userIds = List.of(run.getInitiatorUserId(), run.getPartnerUserId());
        for (Long userId : userIds) {
            if (userId == null) continue;
            try {
                boolean rewarded = quotaService.tryConsume(userId, PetQuotaService.QuotaType.VISIT_REWARD, 0, 3);
                long coins = rewarded ? rewardCoins : Math.max(10, rewardCoins / 10); // 配额外参与奖
                economyService.earn(userId, run.getInitiatorPetId(), "PVE_REWARD", runId, coins,
                        "{\"type\":\"pve\",\"boss\":\"" + run.getBossCode() + "\",\"rewarded\":" + rewarded + "}",
                        "PVE:" + runId + ":" + userId);
            } catch (Exception e) {
                log.warn("PVE 宠物币发放失败（fail-open）: runId={}, userId={}, err={}", runId, userId, e.getMessage());
            }
        }
        log.info("PVE 胜利奖励已发放: runId={}, users={}", runId, userIds);
    }

    private Long activeRunCount(Long userId) {
        return runMapper.selectCount(new LambdaQueryWrapper<PetPveRun>()
                .and(w -> w.eq(PetPveRun::getInitiatorUserId, userId).or().eq(PetPveRun::getPartnerUserId, userId))
                .in(PetPveRun::getStatus, "OPEN", "FIGHTING"));
    }

    private PetPveRun requireRun(Long runId) {
        PetPveRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "副本不存在");
        }
        // 懒清理：OPEN 超时 → EXPIRED
        if ("OPEN".equals(run.getStatus()) && run.getCreatedAt().isBefore(LocalDateTime.now().minusHours(OPEN_EXPIRE_HOURS))) {
            runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                    .eq(PetPveRun::getId, runId).eq(PetPveRun::getStatus, "OPEN")
                    .set(PetPveRun::getStatus, "EXPIRED"));
            run.setStatus("EXPIRED");
        }
        return run;
    }

    private PetBattleEngine.Fighter toFighter(Pet pet) {
        PetStatsService.CombatStats stats = statsService.combatStats(pet);
        // 全参口径（对齐 PetBattleServiceImpl.toFighter）：含装备/技能加成
        return new PetBattleEngine.Fighter(pet.getId(), pet.getName(),
                stats.hp(), stats.maxHp(), stats.strength(), stats.intelligence(), stats.agility(), stats.charm(),
                stats.critBonus(), stats.dodgeBonus(), stats.damageBonus(),
                stats.powerStrikeBonus(), stats.damageReduction(), statsService.firstStrikeBonus(pet));
    }

    private int maxHpOf(Pet pet) {
        return statsService.combatStats(pet).maxHp();
    }

    private List<Map<String, Object>> parseRounds(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String appendRounds(String existing, List<Map<String, Object>> additions) {
        List<Map<String, Object>> all = parseRounds(existing);
        all.addAll(additions);
        try {
            return objectMapper.writeValueAsString(all);
        } catch (Exception e) {
            return existing;
        }
    }

    private Page<PetPveRunVO> toVoPage(Page<PetPveRun> result) {
        Page<PetPveRunVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private PetPveRunVO toVO(PetPveRun run) {
        List<Map<String, Object>> rounds = parseRounds(run.getRounds());
        List<PetPveRunVO.RoundLog> recent = rounds.size() > 10
                ? rounds.subList(rounds.size() - 10, rounds.size()).stream().map(this::toRoundLog).toList()
                : rounds.stream().map(this::toRoundLog).toList();
        return new PetPveRunVO(
                run.getId(), run.getBossCode(), run.getBossName(),
                run.getBossMaxHp(), run.getBossHp(),
                run.getInitiatorUserId(), petNameOf(run.getInitiatorPetId()),
                run.getPartnerUserId(), petNameOf(run.getPartnerPetId()),
                run.getInitiatorPetHp(), run.getPartnerPetHp() == null ? -1 : run.getPartnerPetHp(),
                run.getStatus(), Boolean.TRUE.equals(run.getRewardGranted()), recent,
                run.getCreatedAt(), run.getFinishedAt());
    }

    private PetPveRunVO.RoundLog toRoundLog(Map<String, Object> m) {
        return new PetPveRunVO.RoundLog(
                ((Number) m.getOrDefault("round", 0)).intValue(),
                String.valueOf(m.getOrDefault("actorName", "")),
                String.valueOf(m.getOrDefault("action", "")),
                ((Number) m.getOrDefault("damage", 0)).intValue(),
                Boolean.TRUE.equals(m.get("critical")),
                Boolean.TRUE.equals(m.get("dodged")),
                String.valueOf(m.getOrDefault("targetName", "")),
                ((Number) m.getOrDefault("targetRemainingHp", 0)).intValue());
    }

    private String petNameOf(Long petId) {
        if (petId == null) return null;
        Pet pet = petMapper.selectById(petId);
        return pet != null ? pet.getName() : null;
    }

    private String toJson(PetBattleEngine.Fighter fighter) {
        try {
            return objectMapper.writeValueAsString(fighter);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 内置 Boss 配置 */
    private record BossSpec(String name, int maxHp, int attack, int defense) {}
}
