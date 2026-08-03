package com.cloudmart.coupon.discount;

import java.math.BigDecimal;

/**
 * 满减折扣策略：订单金额达到门槛后扣减固定金额。
 */
public class CashDiscount implements Discount {

    private final Long couponId;
    private final BigDecimal threshold;
    private final BigDecimal discountAmount;

    public CashDiscount(Long couponId, BigDecimal threshold, BigDecimal discountAmount) {
        this.couponId = couponId;
        this.threshold = threshold;
        this.discountAmount = discountAmount;
    }

    @Override
    public BigDecimal calculate(BigDecimal orderAmount) {
        if (orderAmount.compareTo(threshold) >= 0) {
            BigDecimal discount = discountAmount.min(orderAmount);
            return discount;
        }
        return BigDecimal.ZERO;
    }

    @Override
    public Long getCouponId() {
        return couponId;
    }
}
