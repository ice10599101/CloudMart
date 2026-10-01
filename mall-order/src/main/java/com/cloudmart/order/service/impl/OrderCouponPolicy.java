package com.cloudmart.order.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO;
import com.cloudmart.order.feign.CouponFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 优惠券核验与折扣计算（T03/COUPON-01）：报价与下单共用同一实现——
 * 报价阶段预计算的折扣与下单阶段重算的折扣必须一致，否则 QUOTE_STALE。
 *
 * <p>规则（与既有 COUPON-01 语义一致，抽出为单一权威）：</p>
 * <ul>
 *   <li>券必须存在、归属本人、状态 UNUSED、达到使用门槛；</li>
 *   <li>AMOUNT_OFF 直减；PERCENT_OFF 按折扣率（区间 (0,1]，越界按无优惠告警）；</li>
 *   <li>优惠封顶不超过商品总额（防 payAmount 为负）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCouponPolicy {

    private final CouponFeignClient couponFeignClient;

    /** 校验券可用性（归属/状态/门槛）；不可用抛业务异常 */
    public UserCouponDTO validate(Long couponId, Long userId, BigDecimal totalAmount) {
        ApiResponse<UserCouponDTO> couponResp = couponFeignClient.getCouponById(couponId);
        if (couponResp == null || !couponResp.success() || couponResp.data() == null) {
            throw new BusinessException("COUPON_NOT_FOUND", "优惠券不存在");
        }
        UserCouponDTO coupon = couponResp.data();
        if (!coupon.userId().equals(userId)) {
            throw new BusinessException("COUPON_ACCESS_DENIED", "无权使用此优惠券");
        }
        if (!"UNUSED".equals(coupon.status())) {
            throw new BusinessException("COUPON_ALREADY_USED", "优惠券已使用");
        }
        if (coupon.thresholdAmount() != null && totalAmount.compareTo(coupon.thresholdAmount()) < 0) {
            throw new BusinessException("COUPON_THRESHOLD_NOT_MET", "未达优惠券使用门槛");
        }
        return coupon;
    }

    /** 折扣计算（COUPON-01 封顶与费率越界防护）；coupon 为 null 返回 0 */
    public BigDecimal calculate(UserCouponDTO coupon, BigDecimal totalAmount) {
        if (coupon == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = BigDecimal.ZERO;
        if ("AMOUNT_OFF".equals(coupon.templateType()) && coupon.discountAmount() != null) {
            discount = coupon.discountAmount();
        } else if ("PERCENT_OFF".equals(coupon.templateType()) && coupon.discountRate() != null) {
            BigDecimal rate = coupon.discountRate();
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                log.warn("[COUPON01] 折扣率配置越界，按无优惠处理, couponId={}, rate={}",
                        coupon.id(), rate);
                return BigDecimal.ZERO;
            }
            discount = totalAmount.subtract(totalAmount.multiply(rate));
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(totalAmount) > 0) {
            discount = totalAmount;
        }
        return discount;
    }

    /** 组合：校验 + 计算应付（报价与下单共用） */
    public BigDecimal payAmountOf(Long couponId, Long userId, BigDecimal totalAmount) {
        BigDecimal discount = couponId == null
                ? BigDecimal.ZERO
                : calculate(validate(couponId, userId, totalAmount), totalAmount);
        return totalAmount.subtract(discount).max(BigDecimal.ZERO);
    }
}
