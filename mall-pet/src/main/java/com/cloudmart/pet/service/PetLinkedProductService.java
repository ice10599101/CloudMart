package com.cloudmart.pet.service;

import java.util.List;
import java.util.Optional;

/**
 * 实物商品联动（§6/R01/R02）：兑换码权威核验 → 双倍喂食权益。
 *
 * <p>R01 契约：核销必须经 mall-coupon 权威事实校验（码存在、已兑换、归属本人），
 * 操作落可恢复台账（VERIFYING/DELIVERED/REJECTED/PENDING）；跨服务超时返回 PENDING，
 * 按同一 operationId 重查；itemCode 由服务端按权益配置推导，客户端不可指定。</p>
 */
public interface PetLinkedProductService {

    /** 核销操作结果（对外视图） */
    record OperationView(String operationId, String status, Long entitlementId,
                         String rejectReasonCode, boolean usable) {
    }

    /** 权益持有明细（6.2 R02：usableCount + items 含来源/失效时刻） */
    record EntitlementItem(Long entitlementId, String itemCode, Long sourceFactId,
                           String status, java.time.LocalDateTime expiresAt) {
    }

    /** 我的双倍喂食权益汇总（可用份数 + 明细含来源/失效时刻） */
    record EntitlementSummary(int usableCount, List<EntitlementItem> items) {
    }

    /**
     * 核销兑换码（幂等：同 requestKey 重放返回原操作；同一码至多发放一次权益）。
     *
     * @param userId         发起用户
     * @param redemptionCode 兑换码（已在 mall-coupon /user-coupons/exchange 完成兑换）
     * @param requestKey     客户端请求键（可空，服务端生成）
     */
    OperationView redeem(Long userId, String redemptionCode, String requestKey);

    /**
     * 按操作 ID 查询核销结果（仅本人）；PENDING 操作顺带原单重试一次核验
     * （幂等：DELIVERED/REJECTED 原样返回）。
     */
    OperationView getOperation(Long userId, String operationId);

    EntitlementSummary entitlementSummary(Long userId);

    /** 用户是否持有可用权益（列表快捷判定） */
    boolean hasUsableEntitlement(Long userId);

    /**
     * R02：喂食占用一条权益——同事务 CAS 选中一条 AVAILABLE 且未过期的权益置 CONSUMED，
     * 返回被占用的权益 ID；无可用权益返回 empty，只有占用成功才应用双倍。
     * 同一 feedOperationId 重放返回原占用（uk 防重，不二次消耗）。
     */
    Optional<Long> consumeOneForFeed(Long userId, Long petId, String feedOperationId);
}
