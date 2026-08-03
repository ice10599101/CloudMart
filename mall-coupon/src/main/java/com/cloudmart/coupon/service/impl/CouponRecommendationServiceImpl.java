package com.cloudmart.coupon.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.discount.CashDiscount;
import com.cloudmart.coupon.discount.Discount;
import com.cloudmart.coupon.discount.DiscountCalculator;
import com.cloudmart.coupon.discount.DiscountResult;
import com.cloudmart.coupon.discount.RateDiscount;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponRecommendationService;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.RecommendationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券推荐服务实现。
 *
 * <p>从用户可用优惠券中筛选 UNUSED 状态的券，转换为 {@link Discount} 策略对象，
 * 交由 {@link DiscountCalculator} 计算最优组合。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRecommendationServiceImpl implements CouponRecommendationService {

    private static final int MAX_USER_COUPONS_FETCH = 100;

    private final CouponService couponService;
    private final DiscountCalculator discountCalculator;

    @Override
    public RecommendationVO recommend(Long userId, BigDecimal orderAmount) {
        if (userId == null) {
            throw new BusinessException("VALIDATION_ERROR", "用户ID不能为空");
        }
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "订单金额必须大于0");
        }

        List<UserCouponDTO> userCoupons = couponService.listUserCoupons(userId, "UNUSED", 1, MAX_USER_COUPONS_FETCH);
        List<Discount> discounts = toDiscounts(userCoupons);

        DiscountResult result = discountCalculator.calculateBestDiscount(orderAmount, discounts);

        List<Long> couponIds = result.appliedDiscounts().stream()
                .map(Discount::getCouponId)
                .toList();

        return new RecommendationVO(couponIds, orderAmount, result.totalDiscount(), result.finalAmount());
    }

    private List<Discount> toDiscounts(List<UserCouponDTO> userCoupons) {
        List<Discount> discounts = new ArrayList<>();
        for (UserCouponDTO dto : userCoupons) {
            Discount discount = toDiscount(dto);
            if (discount != null) {
                discounts.add(discount);
            }
        }
        return discounts;
    }

    private Discount toDiscount(UserCouponDTO dto) {
        if ("AMOUNT_OFF".equals(dto.templateType()) && dto.discountAmount() != null) {
            BigDecimal threshold = dto.thresholdAmount() != null ? dto.thresholdAmount() : BigDecimal.ZERO;
            return new CashDiscount(dto.id(), threshold, dto.discountAmount());
        }
        if ("PERCENT_OFF".equals(dto.templateType()) && dto.discountRate() != null) {
            BigDecimal threshold = dto.thresholdAmount() != null ? dto.thresholdAmount() : BigDecimal.ZERO;
            return new RateDiscount(dto.id(), threshold, dto.discountRate());
        }
        log.warn("无法识别的优惠券类型，跳过: couponId={}, type={}", dto.id(), dto.templateType());
        return null;
    }
}
