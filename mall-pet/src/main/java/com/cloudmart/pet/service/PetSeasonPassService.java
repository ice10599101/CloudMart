package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetSeasonPassVO;

/**
 * 赛季通行证（§6/R05）：经验事实去重累积 + 逐档领取权威事实 + 可恢复交付。
 */
public interface PetSeasonPassService {

    /** 我的通行证（无进行中赛季返回 seasonId=null 的空视图） */
    PetSeasonPassVO myPass(Long userId);

    /** 按赛季查询通行证（R05：已结束赛季在领奖截止前可查看可领取项） */
    PetSeasonPassVO seasonPass(Long userId, Long seasonId);

    /**
     * 领取档位奖励（R05）：uk(season,user,tier) 权威裁决——并发领不同档互不覆盖，
     * 同档重复返回原状态；币/皮肤经奖励应用服务交付，失败留 CLAIM_PENDING 可恢复补发。
     *
     * @param seasonId   赛季 ID（null=当前进行中赛季）
     * @param requestKey 客户端请求键（可空；仅做日志追踪，裁决以事实键为准）
     */
    PetSeasonPassVO claimTier(Long userId, Long seasonId, int tier, String requestKey);

    /**
     * 任务/宝箱领取事实累积通行证经验（R05）：按 (season,user,source,sourceBizId)
     * 去重，同一事实不重复计入；无进行中赛季忽略；失败不阻断主流程。
     */
    void addExp(Long userId, int exp, String source, String sourceBizId);

    /**
     * 定时补发（R05）：CLAIM_PENDING 领取行重试交付（奖励应用服务 uk 防重）；
     * 全部交付完成置 SETTLED。
     *
     * @return 本次补发完成的领取行数
     */
    int settlePendingClaims();

    /**
     * T17 校验任务：按经验事实重算某赛季某用户的累计经验，返回（事实累计, 当前投影）。
     * 只读校验、不自动修正累计数（禁止人工随意加累计数）——发现漂移由运维核实后
     * 按事实重建。
     */
    ExpReconciliation reconcileExp(Long seasonId, Long userId);

    /** T17 校验结果：factSum=按事实表重算的累计经验；projectedExp=通行证当前投影。 */
    record ExpReconciliation(long factSum, int projectedExp) {
        public boolean drifted() {
            return factSum != projectedExp;
        }
    }
}
