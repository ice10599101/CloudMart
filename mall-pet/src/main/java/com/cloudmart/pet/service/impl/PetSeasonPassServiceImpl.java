package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonPass;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonPassMapper;
import com.cloudmart.pet.service.PetSeasonPassService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetSeasonPassVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 赛季通行证（§6）。
 *
 * <p>经验累积：每日任务领取 +5/次、全清宝箱 +15/次（调用方在领取主流程成功后
 * 调 addExp，失败不阻断任务主流程）。档位阶梯固定 10 档（10*5 起步、每档 +5 递增）：
 * 1..9 档发宠物币（QUEST_REWARD 流水，operationId=PASS:{season}:{user}:{tier} 幂等），
 * 第 10 档额外发放赛季皮肤（inventory/skin 通道，皮肤码可配；
 * 配置缺失/皮肤不存在时跳过皮肤只发币，不阻塞领奖）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetSeasonPassServiceImpl implements PetSeasonPassService {

    private static final int TIER_COUNT = 10;
    private static final int BASE_TIER_EXP = 10;
    private static final int TIER_EXP_STEP = 5;
    private static final int QUEST_CLAIM_EXP = 5;
    private static final int CHEST_CLAIM_EXP = 15;

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonPassMapper passMapper;
    private final com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper;
    private final com.cloudmart.pet.wallet.PetWalletService walletService;
    private final PetService petService;

    /** 最终档赛季皮肤码（须存在于 skins 配置；空串=不发皮肤只发币） */
    @Value("${pet.season-pass.final-tier-skin-code:}")
    private String finalTierSkinCode;

    @Override
    public PetSeasonPassVO myPass(Long userId) {
        PetSeason season = activeSeason();
        if (season == null) {
            return emptyPass();
        }
        PetSeasonPass pass = requirePass(season.getId(), userId);
        return buildVo(season, pass);
    }

    @Override
    @Transactional
    public PetSeasonPassVO claimTier(Long userId, int tier) {
        if (tier < 1 || tier > TIER_COUNT) {
            throw new BusinessException(com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "档位非法");
        }
        PetSeason season = activeSeason();
        if (season == null) {
            throw new BusinessException(com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "当前没有进行中的赛季");
        }
        PetSeasonPass pass = requirePass(season.getId(), userId);
        List<Integer> claimed = parseTiers(pass.getClaimedTiers());
        int required = requiredExp(tier);
        if (pass.getPassExp() < required) {
            throw new BusinessException(com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "通行证经验不足（需要 " + required + "）");
        }
        if (claimed.contains(tier)) {
            throw new BusinessException(com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "该档位奖励已领取");
        }
        claimed.add(tier);
        int updated = passMapper.update(null, new LambdaUpdateWrapper<PetSeasonPass>()
                .eq(PetSeasonPass::getId, pass.getId())
                .eq(PetSeasonPass::getPassExp, pass.getPassExp())
                .set(PetSeasonPass::getClaimedTiers, toJson(claimed)));
        if (updated == 0) {
            throw new BusinessException(com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "领取冲突，请重试");
        }
        grantTierReward(userId, season.getId(), tier);
        log.info("赛季通行证奖励已领取: season={}, user={}, tier={}", season.getId(), userId, tier);
        return buildVo(season, passMapper.selectById(pass.getId()));
    }

    @Override
    public void addExp(Long userId, int exp) {
        if (exp <= 0) {
            return;
        }
        try {
            PetSeason season = activeSeason();
            if (season == null) {
                return;
            }
            PetSeasonPass pass = requirePass(season.getId(), userId);
            passMapper.update(null, new LambdaUpdateWrapper<PetSeasonPass>()
                    .eq(PetSeasonPass::getId, pass.getId())
                    .setSql("pass_exp = pass_exp + " + exp));
        } catch (Exception e) {
            log.warn("赛季通行证经验累积失败（不阻断任务主流程）: userId={}, err={}", userId, e.getMessage());
        }
    }

    private PetSeason activeSeason() {
        return seasonMapper.selectOne(new LambdaQueryWrapper<PetSeason>()
                .eq(PetSeason::getStatus, "ACTIVE")
                .last("LIMIT 1"));
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
        try {
            passMapper.insert(created);
            return created;
        } catch (DuplicateKeyException concurrent) {
            return passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                    .eq(PetSeasonPass::getSeasonId, seasonId)
                    .eq(PetSeasonPass::getUserId, userId));
        }
    }

    private PetSeasonPassVO buildVo(PetSeason season, PetSeasonPass pass) {
        List<Integer> claimed = parseTiers(pass.getClaimedTiers());
        List<PetSeasonPassVO.Tier> tiers = new ArrayList<>();
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            int required = requiredExp(tier);
            tiers.add(new PetSeasonPassVO.Tier(
                    tier, required, pass.getPassExp() >= required, claimed.contains(tier), tierRewardDesc(tier)));
        }
        return new PetSeasonPassVO(season.getId(), season.getName(), pass.getPassExp(), claimed, tiers);
    }

    private PetSeasonPassVO emptyPass() {
        List<PetSeasonPassVO.Tier> tiers = new ArrayList<>();
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            tiers.add(new PetSeasonPassVO.Tier(tier, requiredExp(tier), false, false, tierRewardDesc(tier)));
        }
        return new PetSeasonPassVO(null, null, 0, List.of(), tiers);
    }

    private int requiredExp(int tier) {
        return BASE_TIER_EXP + (tier - 1) * TIER_EXP_STEP;
    }

    private String tierRewardDesc(int tier) {
        long coins = tierCoins(tier);
        return tier == TIER_COUNT ? coins + " 宠物币 + 赛季限定皮肤" : coins + " 宠物币";
    }

    private long tierCoins(int tier) {
        return 20L * tier;
    }

    private void grantTierReward(Long userId, Long seasonId, int tier) {
        long coins = tierCoins(tier);
        String bizKey = "PASS:" + seasonId + ":" + userId + ":" + tier;
        try {
            walletService.credit(new com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand(
                    userId, null, "EARN", "QUEST_REWARD", bizKey, coins,
                    "PASS_REWARD:" + bizKey, bizKey, null, "season-pass", null));
        } catch (Exception e) {
            log.warn("赛季通行证宠物币发放失败（不阻断领奖记录）: user={}, tier={}, err={}", userId, tier, e.getMessage());
        }
        if (tier == TIER_COUNT && finalTierSkinCode != null && !finalTierSkinCode.isBlank()) {
            grantSkin(userId, finalTierSkinCode.trim());
        }
    }

    /** 最终档皮肤：按当前主宠直插 inventory（SKIN，对齐进化发放口径）；重复/失败静默跳过 */
    private void grantSkin(Long userId, String skinCode) {
        try {
            Pet pet = petService.requireOwnedPet(userId);
            long exists = inventoryMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetInventory>()
                    .eq(com.cloudmart.pet.entity.PetInventory::getPetId, pet.getId())
                    .eq(com.cloudmart.pet.entity.PetInventory::getItemType, "SKIN")
                    .eq(com.cloudmart.pet.entity.PetInventory::getItemCode, skinCode));
            if (exists > 0) {
                return;
            }
            com.cloudmart.pet.entity.PetInventory item = new com.cloudmart.pet.entity.PetInventory();
            item.setPetId(pet.getId());
            item.setUserId(userId);
            item.setItemType("SKIN");
            item.setItemCode(skinCode);
            item.setQuantity(1);
            item.setEquipped(false);
            item.setAcquiredAt(LocalDateTime.now());
            inventoryMapper.insert(item);
            log.info("赛季通行证皮肤已发放: petId={}, skin={}", pet.getId(), skinCode);
        } catch (Exception e) {
            log.warn("赛季通行证皮肤发放失败（跳过）: user={}, skin={}, err={}", userId, skinCode, e.getMessage());
        }
    }

    private List<Integer> parseTiers(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json == null ? "[]" : json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<Integer> tiers) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tiers);
        } catch (Exception e) {
            return "[]";
        }
    }
}
