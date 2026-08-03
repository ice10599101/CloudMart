package com.cloudmart.coupon.discount;

import java.math.BigDecimal;
import java.util.List;

/**
 * 折扣计算结果。
 *
 * @param totalDiscount     总优惠金额
 * @param finalAmount       最终支付金额
 * @param appliedDiscounts  实际生效的折扣列表（按应用顺序）
 */
public record DiscountResult(
    BigDecimal totalDiscount,
    BigDecimal finalAmount,
    List<Discount> appliedDiscounts
) {
    /**
     * 空结果，无任何优惠。
     */
    public static DiscountResult empty(BigDecimal orderAmount) {
        return new DiscountResult(BigDecimal.ZERO, orderAmount, List.of());
    }
}
