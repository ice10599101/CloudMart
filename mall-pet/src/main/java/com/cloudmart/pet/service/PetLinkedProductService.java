package com.cloudmart.pet.service;

/**
 * 实物商品联动（§6）：兑换码核销 → 双倍喂食权益。
 */
public interface PetLinkedProductService {

    /**
     * 兑换码核销（coupon /user-coupons/exchange 成功后由调用方回调；或用户在 pet 侧
     * 输入兑换码时经 Feign 校验券已归属本人）。幂等：uk redemption_code。
     *
     * @return 权益 ID
     */
    Long redeem(Long userId, String redemptionCode, Long userCouponId, String itemCode);

    /** 用户是否持有未使用的双倍喂食权益 */
    boolean hasUsableEntitlement(Long userId);

    /** 消耗一次双倍喂食权益（feedItem 效果 x2 时调用；CAS used=0→1） */
    void consumeOne(Long userId, String itemCode);
}
