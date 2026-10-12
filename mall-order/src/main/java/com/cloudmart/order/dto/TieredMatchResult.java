package com.cloudmart.order.dto;

import java.math.BigDecimal;

/**
 * R12：购物篮满减匹配结果（mall-order → mall-marketing 内部契约镜像）。
 *
 * @param matched       是否命中生效活动
 * @param discountAmount 满减优惠金额（未命中为 0）
 */
public record TieredMatchResult(Long promotionId, String promotionName, Long matchedRuleId,
                                BigDecimal minAmount, BigDecimal discountAmount, boolean matched) {
    public static TieredMatchResult none() {
        return new TieredMatchResult(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }
}
