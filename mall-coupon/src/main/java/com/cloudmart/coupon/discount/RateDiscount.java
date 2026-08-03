package com.cloudmart.coupon.discount;

import java.math.BigDecimal;

/**
 * 折扣率策略：订单金额达到门槛后按比例打折。
 *
 * <p>discountRate 表示用户实际支付的比例，如 0.85 表示 8.5 折（优惠 15%）。</p>
 */
public class RateDiscount implements Discount {

    private final Long couponId;
    private final BigDecimal threshold;
    private final BigDecimal discountRate;

    public RateDiscount(Long couponId, BigDecimal threshold, BigDecimal discountRate) {
        this.couponId = couponId;
        this.threshold = threshold;
        this.discountRate = discountRate;
    }

    @Override
    public BigDecimal calculate(BigDecimal orderAmount) {
        if (orderAmount.compareTo(threshold) >= 0) {
            return orderAmount.multiply(BigDecimal.ONE.subtract(discountRate));
        }
        return BigDecimal.ZERO;
    }

    @Override
    public Long getCouponId() {
        return couponId;
    }
}
