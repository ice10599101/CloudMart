package com.cloudmart.order.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.DiscountLine;
import com.cloudmart.order.dto.TieredMatchRequest;
import com.cloudmart.order.dto.TieredMatchResult;
import com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO;
import com.cloudmart.order.feign.MarketingFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * R12：报价统一优惠计算（报价生成与下单复核的唯一权威，杜绝两处算法漂移）。
 *
 * <p>计算顺序（默认叠加规则）：商品金额 → 适用阶梯满减 → 优惠券 → 应付。</p>
 * <ul>
 *   <li>满减：mall-marketing 按购物篮金额自动匹配当前生效最优活动（多活动重叠
 *       取优惠最大者，确定性次序）；匹配/营销服务不可达 fail-closed；</li>
 *   <li>优惠券：使用门槛按商品金额校验（既有口径），折扣额按"满减后剩余金额"
 *       计算并封顶——券不能对已被满减免掉的部分重复减免；</li>
 *   <li>每步减免均不产生负应付；输出 discountLines 快照（活动/规则 ID、券 ID、
 *       金额、门槛、名称），供前端展示、订单快照与售后/审计反查。</li>
 * </ul>
 * 秒杀/拼团订单不走报价链路（活动快照价直接计价），天然与普通满减互斥。
 */
@Slf4j
@Component
public class QuoteDiscountCalculator {

    private final MarketingFeignClient marketingFeignClient;
    private final OrderCouponPolicy couponPolicy;
    private final ObjectMapper objectMapper;

    public QuoteDiscountCalculator(MarketingFeignClient marketingFeignClient,
                                   OrderCouponPolicy couponPolicy,
                                   ObjectMapper objectMapper) {
        this.marketingFeignClient = marketingFeignClient;
        this.couponPolicy = couponPolicy;
        this.objectMapper = objectMapper;
    }

    /** 优惠计算结果（金额单位与入参一致，CNY 元）。 */
    public record Result(BigDecimal tieredAmount, BigDecimal couponAmount,
                         BigDecimal totalDiscount, BigDecimal payAmount,
                         List<DiscountLine> lines, String linesJson) {
    }

    public Result calculate(Long userId, BigDecimal goodsTotal, Long couponId) {
        if (goodsTotal == null || goodsTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_AMOUNT", "商品金额非法");
        }
        List<DiscountLine> lines = new ArrayList<>();

        // ① 满减（fail-closed：营销服务不可达宁可不报价，也不漏算优惠造成少收/前后不一致）
        ApiResponse<TieredMatchResult> resp =
                marketingFeignClient.matchTiered(new TieredMatchRequest(goodsTotal));
        if (resp == null || !resp.success() || resp.data() == null) {
            throw new BusinessException("TIERED_PROMOTION_UNAVAILABLE", "优惠活动服务暂不可用，无法生成报价");
        }
        TieredMatchResult match = resp.data();
        BigDecimal tieredAmount = match.matched() && match.discountAmount() != null
                ? match.discountAmount().min(goodsTotal).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        if (tieredAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(DiscountLine.tiered(match));
        }

        // ② 优惠券：门槛按商品金额校验（既有口径），减免按满减后剩余基数计算
        BigDecimal couponAmount = BigDecimal.ZERO;
        if (couponId != null) {
            UserCouponDTO coupon = couponPolicy.validate(couponId, userId, goodsTotal);
            BigDecimal baseAfterTiered = goodsTotal.subtract(tieredAmount);
            couponAmount = couponPolicy.calculate(coupon, baseAfterTiered)
                    .min(baseAfterTiered).max(BigDecimal.ZERO);
            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(DiscountLine.coupon(coupon.id(), coupon.templateName(), couponAmount));
            }
        }

        BigDecimal totalDiscount = tieredAmount.add(couponAmount);
        BigDecimal payAmount = goodsTotal.subtract(totalDiscount).max(BigDecimal.ZERO);
        String json = writeLines(lines);
        return new Result(tieredAmount, couponAmount, totalDiscount, payAmount, lines, json);
    }

    public String writeLines(List<DiscountLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines == null ? List.of() : lines);
        } catch (Exception e) {
            // 快照序列化失败属于真实异常（不应静默丢审计信息）
            throw new BusinessException("DISCOUNT_LINES_SERIALIZE_ERROR", "优惠明细序列化失败: " + e.getMessage());
        }
    }

    /** 读取报价/订单上的优惠明细快照（历史无优惠单据返回空列表）。 */
    public List<DiscountLine> readLines(String linesJson) {
        if (linesJson == null || linesJson.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(linesJson, DiscountLine[].class));
        } catch (Exception e) {
            log.warn("[R12] 优惠明细快照反序列化失败，按无明细处理: {}", e.getMessage());
            return List.of();
        }
    }
}
