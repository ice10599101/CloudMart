package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetAssetGrant;
import com.cloudmart.pet.entity.PetRewardClaim;
import com.cloudmart.pet.repository.PetAssetGrantMapper;
import com.cloudmart.pet.repository.PetRewardClaimMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.PetRewardApplicationService;
import com.cloudmart.pet.wallet.PetWalletService;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletCommand;
import com.cloudmart.pet.wallet.PetWalletService.PetWalletResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 奖励领取应用服务实现（W01）。
 *
 * <p>事务顺序（§5.3）：业务事务内 ①claim 行占位（唯一键裁决）→ ②首次才解析奖励（冻结快照）
 * → ③钱包入账（MANDATORY 加入本事务）→ ④物品发放 + asset_grant → ⑤claim 完成 + result。
 * 任何一步失败整体回滚，领取事实随事务消失，可安全重试（T08/T09）。</p>
 */
@Service
@Slf4j
public class PetRewardApplicationServiceImpl implements PetRewardApplicationService {

    private static final String ENDPOINT_KEY = "REWARD_CLAIM";

    private final PetRewardClaimMapper claimMapper;
    private final PetWalletService walletService;
    private final PetAssetGrantMapper assetGrantMapper;
    private final PetRequestDedupService dedupService;
    private final ObjectProvider<RewardAssetDeliverer> deliverers;
    private final TransactionTemplate transactionTemplate;

    public PetRewardApplicationServiceImpl(PetRewardClaimMapper claimMapper,
                                           PetWalletService walletService,
                                           PetAssetGrantMapper assetGrantMapper,
                                           PetRequestDedupService dedupService,
                                           ObjectProvider<RewardAssetDeliverer> deliverers,
                                           TransactionTemplate transactionTemplate) {
        this.claimMapper = claimMapper;
        this.walletService = walletService;
        this.assetGrantMapper = assetGrantMapper;
        this.dedupService = dedupService;
        this.deliverers = deliverers;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public RewardClaimResult claim(RewardClaimCommand command, RewardResolver resolver) {
        if (command.bizType() == null || command.bizType().isBlank()
                || command.bizId() == null || command.bizId().isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "奖励业务事实键不完整");
        }
        String slot = command.rewardSlot() == null || command.rewardSlot().isBlank()
                ? "MAIN" : command.rewardSlot();

        RewardClaimResult result = transactionTemplate.execute(status -> doClaim(command, slot, resolver));
        if (command.requestKey() != null && !command.requestKey().isBlank()
                && PetRequestDedupService.isValidRequestKey(command.requestKey())) {
            // 网络重试收敛锚点：同请求键下次直接返回（事实键本身已保证一次性）。
            // 无租约上下文（不经 claim），expectedLeaseOwner 传 null 保持原语义
            dedupService.completeSucceeded(command.userId(), ENDPOINT_KEY,
                    command.requestKey(), null, claimIdOf(result), PetJsonUtils.toJson(result));
        }
        return result;
    }

    private RewardClaimResult doClaim(RewardClaimCommand command, String slot, RewardResolver resolver) {
        PetRewardClaim claim = new PetRewardClaim();
        claim.setUserId(command.userId());
        claim.setPetId(command.petId());
        claim.setBizType(command.bizType());
        claim.setBizId(command.bizId());
        claim.setRewardSlot(slot);
        claim.setWalletDomain("PET");
        claim.setStatus("PROCESSING");
        try {
            claimMapper.insert(claim);
        } catch (DuplicateKeyException duplicate) {
            return returnExisting(command, slot);
        }

        // 首次领取：解析最终奖励并冻结（随机结果只生成一次，重试不重抽）
        ResolvedReward reward = resolver.resolve(command);
        String snapshot = PetJsonUtils.toJson(Map.of(
                "coin", reward.coin(),
                "growth", reward.growthSnapshotJson() == null ? "" : reward.growthSnapshotJson(),
                "items", reward.items(),
                "ruleVersion", reward.ruleVersion() == null ? "" : reward.ruleVersion()));
        claim.setRewardSnapshot(snapshot);

        String operationId = "pw_" + dedupService.canonicalHash(
                "REWARD|" + command.userId() + "|" + command.bizType() + "|" + command.bizId() + "|" + slot);
        String bizKey = command.bizId() + ":" + slot;

        long balanceAfter;
        String walletOperationId = operationId;
        if (reward.coin() > 0) {
            PetWalletResult wallet = walletService.credit(new PetWalletCommand(command.userId(),
                    command.petId(), "EARN", command.bizType(), bizKey, reward.coin(),
                    operationId, dedupService.canonicalHash(ENDPOINT_KEY, command.bizType(), command.bizId(), slot),
                    null, reward.ruleVersion(), snapshot));
            balanceAfter = wallet.balanceAfter();
            if (wallet.duplicate()) {
                walletOperationId = wallet.operationId();
            }
        } else {
            balanceAfter = walletService.getOrCreateAccount(command.userId()).getBalance();
        }

        List<String> slots = grantItems(claim.getId(), command, slot, reward);

        claim.setStatus("COMPLETED");
        claim.setResultJson(PetJsonUtils.toJson(Map.of(
                "coin", reward.coin(), "balanceAfter", balanceAfter, "slots", slots)));
        claimMapper.updateById(claim);

        log.info("奖励领取完成, claimId={}, userId={}, bizType={}, bizId={}, coin={}",
                claim.getId(), command.userId(), command.bizType(), command.bizId(), reward.coin());
        return new RewardClaimResult(String.valueOf(claim.getId()), reward.coin(), balanceAfter,
                walletOperationId, slots, false);
    }

    /** 重复领取：返回历史结果（duplicate=true，不重算不重发，T09） */
    private RewardClaimResult returnExisting(RewardClaimCommand command, String slot) {
        PetRewardClaim existing = claimMapper.selectOne(new LambdaQueryWrapper<PetRewardClaim>()
                .eq(PetRewardClaim::getUserId, command.userId())
                .eq(PetRewardClaim::getBizType, command.bizType())
                .eq(PetRewardClaim::getBizId, command.bizId())
                .eq(PetRewardClaim::getRewardSlot, slot));
        if (existing == null) {
            // 冲突但行不可读：并发事务尚未提交
            throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS, "奖励领取处理中，请稍后查询");
        }
        Map<String, Object> result = PetJsonUtils.parse(existing.getResultJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        long coin = result == null || result.get("coin") == null ? 0
                : ((Number) result.get("coin")).longValue();
        long balanceAfter = result == null || result.get("balanceAfter") == null ? 0
                : ((Number) result.get("balanceAfter")).longValue();
        List<String> slots = result == null || result.get("slots") == null ? List.of()
                : ((List<?>) result.get("slots")).stream().map(String::valueOf).toList();
        String operationId = "pw_" + dedupService.canonicalHash(
                "REWARD|" + command.userId() + "|" + command.bizType() + "|" + command.bizId() + "|" + slot);
        log.info("奖励重复领取命中原结果, claimId={}, userId={}, bizType={}, bizId={}",
                existing.getId(), command.userId(), command.bizType(), command.bizId());
        return new RewardClaimResult(String.valueOf(existing.getId()), coin, balanceAfter,
                operationId, slots, true);
    }

    /** 物品发放：asset_grant 事实（uk(source,sourceId,user,slot) 幂等）+ 交付器回调业务库存 */
    private List<String> grantItems(Long claimId, RewardClaimCommand command, String mainSlot,
                                    ResolvedReward reward) {
        List<String> slots = new ArrayList<>();
        Map<String, Integer> byCode = new HashMap<>();
        for (String item : reward.items()) {
            String[] parts = item.split(":", 3);
            if (parts.length < 2) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "物品奖励格式非法: " + item);
            }
            int quantity = parts.length == 3 ? Integer.parseInt(parts[2]) : 1;
            byCode.merge(parts[1], quantity, Integer::sum);
            slots.add(parts[1]);
        }
        int index = 0;
        for (Map.Entry<String, Integer> entry : byCode.entrySet()) {
            PetAssetGrant grant = new PetAssetGrant();
            grant.setUserId(command.userId());
            grant.setPetId(command.petId());
            grant.setSourceType("CLAIM");
            grant.setSourceId(claimId);
            grant.setRewardSlot(mainSlot + ":" + entry.getKey() + ":" + index++);
            grant.setItemType("ITEM");
            grant.setItemCode(entry.getKey());
            grant.setQuantity(entry.getValue());
            try {
                assetGrantMapper.insert(grant);
            } catch (DuplicateKeyException e) {
                // 同 claim 同槽已发放：幂等跳过
                continue;
            }
            final String itemCode = entry.getKey();
            final int quantity = entry.getValue();
            for (RewardAssetDeliverer deliverer : deliverers) {
                deliverer.deliver(command.userId(), command.petId(), claimId, itemCode, quantity);
            }
        }
        return slots;
    }

    private Long claimIdOf(RewardClaimResult result) {
        try {
            return result.claimId() == null ? null : Long.valueOf(result.claimId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 奖励资产交付 SPI（W02 注册：背包/家具/图鉴投影等；在业务事务内执行，失败整体回滚）。
     */
    public interface RewardAssetDeliverer {
        void deliver(Long userId, Long petId, Long claimId, String itemCode, int quantity);
    }
}
