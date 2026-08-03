package com.cloudmart.coupon.discount;

import java.math.BigDecimal;

/**
 * 折扣策略接口，不同类型的优惠券实现不同的折扣计算逻辑。
 *
 * <p>策略模式：满减券、折扣券各有独立实现，新增类型只需新增实现类。</p>
 */
public interface Discount {

    /**
     * 计算当前订单金额下的优惠金额。
     *
     * @param orderAmount 当前订单金额（可能是叠加其他优惠后的剩余金额）
     * @return 优惠金额，不满足门槛时返回 {@link BigDecimal#ZERO}
     */
    BigDecimal calculate(BigDecimal orderAmount);

    /**
     * @return 关联的用户优惠券ID
     */
    Long getCouponId();
}
