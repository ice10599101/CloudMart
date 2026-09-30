package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRankingMapper;
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
import com.cloudmart.pet.wallet.PetEconomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * 赛季结算服务（F2）：赛季到期后按等级榜（level desc, exp desc, id asc——与排行榜同口径）
 * 全量排名 → 快照最终榜（uk 幂等）→ 按奖励梯度经 PetEconomyService.credit 幂等入账
 * （operationKey=SEASON_REWARD:{seasonId}:{petId}，重复结算不重发）→ outbox 通知获奖者。
 *
 * <p>调度器每小时检查 ACTIVE 且已到期的赛季，CAS 置 SETTLED 后结算（多实例防重）；
 * 结算主流程分批事务（每批 500 名），单批失败不阻断后续批次（重跑按快照 uk + 入账幂等收敛）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetSeasonSettlementService {

    /** 结算批次大小（每批一个事务） */
    private static final int SETTLE_BATCH = 500;

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonRewardMapper rewardMapper;
    private final PetSeasonRankingMapper rankingMapper;
    private final PetMapper petMapper;
    private final PetEconomyService economyService;
    private final PetEventProducer eventProducer;
    private final PetStateService stateService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /** 每小时检查到期赛季（scheduler 调用；内部 CAS 防多实例重结算） */
    public void settleExpiredSeasons() {
        List<PetSeason> expired = seasonMapper.selectList(new LambdaQueryWrapper<PetSeason>()
                .eq(PetSeason::getStatus, "ACTIVE")
                .le(PetSeason::getEndsAt, LocalDateTime.now(ZoneOffset.UTC))
                .last("LIMIT 10"));
        for (PetSeason season : expired) {
            // CAS：仅首个调用者获得结算权（多实例部署防重）
            int claimed = seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                    .set(PetSeason::getStatus, "SETTLED")
                    .set(PetSeason::getSettledAt, LocalDateTime.now(ZoneOffset.UTC))
                    .eq(PetSeason::getId, season.getId())
                    .eq(PetSeason::getStatus, "ACTIVE"));
            if (claimed == 0) {
                continue;
            }
            try {
                settle(season);
            } catch (Exception e) {
                // 结算失败回退 ACTIVE（下一轮重试；已入账部分由幂等键收敛不重发）
                seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                        .set(PetSeason::getStatus, "ACTIVE")
                        .set(PetSeason::getSettledAt, null)
                        .eq(PetSeason::getId, season.getId())
                        .eq(PetSeason::getStatus, "SETTLED"));
                log.error("赛季结算失败（已回退 ACTIVE 待重试）: seasonId={}", season.getId(), e);
            }
        }
    }

    /** 指定赛季结算（管理端手动触发用；调用方已 CAS 占有 SETTLED） */
    public void settleExpiredSeasonsFor(PetSeason season) {
        settle(season);
    }

    /** 结算主体：快照 + 发奖分批推进 */
    private void settle(PetSeason season) {
        List<PetSeasonReward> tiers = rewardMapper.selectList(new LambdaQueryWrapper<PetSeasonReward>()
                .eq(PetSeasonReward::getSeasonId, season.getId())
                .orderByAsc(PetSeasonReward::getRankMin));
        long totalPets = petMapper.selectCount(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getIsPublic, true)
                .ne(Pet::getId, 0L));
        int batches = (int) ((totalPets + SETTLE_BATCH - 1) / SETTLE_BATCH);
        int rewarded = 0;
        for (int batch = 0; batch < Math.max(batches, 1); batch++) {
            rewarded += settleBatch(season, tiers, batch * SETTLE_BATCH, SETTLE_BATCH);
        }
        log.info("赛季结算完成: seasonId={}, name={}, totalPets={}, rewarded={}",
                season.getId(), season.getName(), totalPets, rewarded);
    }

    /**
     * 单批结算：等级榜分页（与排行榜 DB 兜底同排序口径），每批一个事务——
     * 快照行 + 奖励入账 + 通知同事务，失败整批回滚（重跑幂等收敛）。
     *
     * <p>注意：本方法由同类内部调用（自调用绕过 Spring 代理，@Transactional 不生效），
     * 因此事务边界用 TransactionTemplate 显式声明——钱包 credit/debit 为
     * MANDATORY 传播，无事务上下文会直接抛异常（远程验收发现的结算 500 根因）。</p>
     *
     * @return 本批发奖人数
     */
    public int settleBatch(PetSeason season, List<PetSeasonReward> tiers, long offset, int limit) {
        Integer rewarded = transactionTemplate.execute(status -> doSettleBatch(season, tiers, offset, limit));
        return rewarded != null ? rewarded : 0;
    }

    private int doSettleBatch(PetSeason season, List<PetSeasonReward> tiers, long offset, int limit) {
        // 分页按 (level desc, exp desc, id asc) 游标推进；is_public=1 与榜单口径一致
        List<Pet> pets = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .select(Pet::getId, Pet::getUserId, Pet::getLevel, Pet::getExp, Pet::getName)
                .eq(Pet::getIsPublic, true)
                .ne(Pet::getId, 0L)
                .orderByDesc(Pet::getLevel)
                .orderByDesc(Pet::getExp)
                .orderByAsc(Pet::getId)
                .last("LIMIT " + limit + " OFFSET " + offset));
        int rewarded = 0;
        int rankBase = (int) offset;
        for (int i = 0; i < pets.size(); i++) {
            int rank = rankBase + i + 1;
            Pet pet = pets.get(i);
            // 快照（uk 幂等）：新插入 = 本宠物尚未结算过，本轮才发奖（星光+经验统一去重依据）
            boolean newlySnapshot;
            try {
                PetSeasonRanking ranking = new PetSeasonRanking();
                ranking.setSeasonId(season.getId());
                ranking.setPetId(pet.getId());
                ranking.setUserId(pet.getUserId());
                ranking.setRankNo(rank);
                ranking.setLevel(pet.getLevel() != null ? pet.getLevel() : 1);
                ranking.setExp(pet.getExp() != null ? pet.getExp() : 0);
                rankingMapper.insert(ranking);
                newlySnapshot = true;
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                newlySnapshot = false;
            }
            if (!newlySnapshot) {
                continue;
            }
            PetSeasonReward tier = tierOf(tiers, rank);
            if (tier == null) {
                continue;
            }
            long starlight = tier.getRewardStarlight() != null ? tier.getRewardStarlight() : 0;
            int expReward = tier.getRewardExp() != null ? tier.getRewardExp() : 0;
            if (starlight > 0) {
                // 幂等入账：operationKey=SEASON_REWARD:{seasonId}:{petId}，同 key 重跑不重发；
                // UNKNOWN 抛 503 回滚本批（下轮重试，快照行随之回滚重新插入）
                PetOperationService.WalletSettlement settlement = economyService.earn(
                        pet.getUserId(), pet.getId(),
                        "SEASON_REWARD", season.getId(), starlight, null,
                        season.getId(), pet.getId());
                if (settlement.isUnknown()) {
                    throw economyService.settlementPending();
                }
            }
            if (expReward > 0) {
                Pet full = petMapper.selectById(pet.getId());
                if (full != null) {
                    // 统一经验路径（含 SICK 减半与升级结算）；重跑不会到达（快照行已存在）
                    stateService.grantExp(full, expReward);
                }
            }
            rewarded++;
            eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_PROACTIVE,
                    new PetEventProducer.PetEventMessage(
                            "SEASON_REWARD:" + season.getId() + ":" + pet.getId(),
                            String.valueOf(pet.getUserId()), "PET_SEASON_REWARD",
                            "赛季结算奖励到账啦！",
                            pet.getName() + "：" + "主人！「" + season.getName() + "」赛季我拿到了第 " + rank
                                    + " 名，奖励已发放，快去看看吧！",
                            String.valueOf(season.getId()), "PET_SEASON_REWARD"));
        }
        return rewarded;
    }

    /** 命中奖励梯度（rank_min ≤ rank ≤ rank_max；区间互斥由 uk(rank_min) 与录入校验保证） */
    private PetSeasonReward tierOf(List<PetSeasonReward> tiers, int rank) {
        return tiers.stream()
                .filter(t -> rank >= t.getRankMin() && rank <= t.getRankMax())
                .findFirst()
                .orElse(null);
    }

    /** 校验奖励梯度（管理端保存前调用）：区间合法且互斥、按 rank_min 升序 */
    public void validateTiers(List<PetSeasonReward> tiers) {
        List<PetSeasonReward> sorted = tiers.stream()
                .sorted(Comparator.comparingInt(PetSeasonReward::getRankMin))
                .toList();
        int expect = 1;
        for (PetSeasonReward tier : sorted) {
            if (tier.getRankMin() == null || tier.getRankMax() == null
                    || tier.getRankMin() < 1 || tier.getRankMax() < tier.getRankMin()) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                        "奖励梯度区间非法");
            }
            if (tier.getRankMin() != expect) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                        "奖励梯度必须从第 1 名起连续覆盖（间隙/重叠都拒绝）");
            }
            expect = tier.getRankMax() + 1;
        }
    }
}
