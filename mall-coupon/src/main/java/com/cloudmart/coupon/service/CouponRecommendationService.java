package com.cloudmart.coupon.service;

import com.cloudmart.coupon.vo.RecommendationVO;

import java.math.BigDecimal;

/**
 * 优惠券推荐服务，为用户计算最优优惠券使用方案。
 */
public interface CouponRecommendationService {

    /**
     * 推荐最优优惠券组合。
     *
     * @param userId      用户ID
     * @param orderAmount 订单金额
     * @return 推荐结果，无可用优惠券时返回零优惠
     */
    RecommendationVO recommend(Long userId, BigDecimal orderAmount);
}
