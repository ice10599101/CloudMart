package com.cloudmart.pet.wallet;

import java.util.List;

/**
 * 奖励领取应用服务（W01/§5.3 奖励事务顺序）。
 *
 * <p>pet_reward_claim 唯一键 {@code (user, bizType, bizId, rewardSlot)} 裁决一次性领取：
 * 合作、活动、宝箱、对战等复用同一事实表；随机结果只生成一次并持久化，重试不重抽（T09）。
 * 奖励冻结归属 petId——切换主宠不改变归属（§7.6）。</p>
 */
public interface PetRewardApplicationService {

    /**
     * 幂等领取奖励。
     *
     * @param resolver 仅在首次领取（NEW 事实）时调用，在业务事务内计算最终奖励；
     *                 重复领取返回历史 result，不再次调用（不重抽）
     */
    RewardClaimResult claim(RewardClaimCommand command, RewardResolver resolver);

    /** 领取命令（业务事实键由服务端构造；客户端仅传意图） */
    record RewardClaimCommand(
            Long userId,
            /** 冻结奖励归属宠物（可空：账号级奖励） */
            Long petId,
            /** 业务类型：ACTIVITY_REWARD/COOP_REWARD/CHEST_REWARD/BATTLE_REWARD/EVENT_REWARD 等 */
            String bizType,
            /** 业务实例 ID（activityId/cooperationId+memberUserId/questInstanceId 等） */
            String bizId,
            /** 奖励槽位（默认 MAIN） */
            String rewardSlot,
            /** 客户端请求键（用于 pet_request_dedup 网络重试收敛；可空——事实键已保证一次性） */
            String requestKey
    ) {
    }

    /** 奖励解析结果（首次领取时计算并冻结） */
    record ResolvedReward(
            /** 宠物币数量（0=无币） */
            long coin,
            /** 经验/亲密度等成长（由调用方领奖后处理；本服务只管币与物品） */
            String growthSnapshotJson,
            /** 物品奖励列表（itemType:itemCode:quantity） */
            List<String> items,
            /** 规则版本快照 */
            String ruleVersion
    ) {
    }

    /** 领取结果（duplicate=true 返回历史结果） */
    record RewardClaimResult(
            String claimId,
            long coin,
            long balanceAfter,
            String walletOperationId,
            List<String> deliveredSlots,
            boolean duplicate
    ) {
    }

    /** 奖励解析回调（业务事务内执行；抛出业务异常则整体回滚，不产生领取事实） */
    interface RewardResolver {
        ResolvedReward resolve(RewardClaimCommand command);
    }
}
