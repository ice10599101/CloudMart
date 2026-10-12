package com.cloudmart.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * R12：报价/订单快照中的一条优惠明细（计算顺序：商品金额→满减→优惠券→应付）。
 *
 * @param type         TIERED（阶梯满减）/ COUPON（优惠券）
 * @param promotionId  满减活动 ID（type=TIERED）
 * @param matchedRuleId 命中的满减规则 ID（type=TIERED，订单反查命中规则用）
 * @param couponId     优惠券 ID（type=COUPON）
 * @param name         展示名（活动名/券名，快照后不依赖营销侧再查询）
 * @param minAmount    满减门槛金额（type=TIERED；前端展示"还差 ¥x 享优惠"）
 * @param amount       本优惠减免金额
 */
public record DiscountLine(String type, Long promotionId, Long matchedRuleId, Long couponId,
                           String name, BigDecimal minAmount, BigDecimal amount) {

    public static final String TYPE_TIERED = "TIERED";
    public static final String TYPE_COUPON = "COUPON";

    public static DiscountLine tiered(TieredMatchResult match) {
        return new DiscountLine(TYPE_TIERED, match.promotionId(), match.matchedRuleId(), null,
                match.promotionName(), match.minAmount(), match.discountAmount());
    }

    public static DiscountLine coupon(Long couponId, String name, BigDecimal amount) {
        return new DiscountLine(TYPE_COUPON, null, null, couponId, name, null, amount);
    }

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    /** 反序列化订单/报价上的优惠明细快照（损坏/空返回空列表，绝不抛给调用方）。 */
    public static List<DiscountLine> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(json, DiscountLine[].class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
