package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(contextId = "orderCouponFeignClient", name = "mall-coupon", fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    @PostMapping("/user-coupons/use")
    ApiResponse<Void> useCoupon(@RequestBody UseCouponRequest request);

    /** T06：支付成功核销确认（RESERVED→USED）。 */
    @PostMapping("/user-coupons/confirm")
    ApiResponse<Void> confirmCoupon(@RequestBody UseCouponRequest request);

    @PostMapping("/user-coupons/return")
    ApiResponse<Void> returnCoupon(@RequestBody ReturnCouponRequest request);

    /** T03：用户侧 GET /user-coupons/{id} 已改为本人过滤，订单侧改走内部查询（mall-order + coupon:trade）。 */
    @GetMapping("/internal/user-coupons/{id}")
    ApiResponse<UserCouponDTO> getCouponById(@PathVariable("id") Long id);

    record UseCouponRequest(Long userCouponId, Long orderId) {}

    record ReturnCouponRequest(Long userCouponId, Long orderId) {}

    record UserCouponDTO(
            Long id, Long userId, Long templateId, String status,
            Long orderId, String receivedAt, String usedAt, String expiredAt,
            String templateName, String templateType,
            BigDecimal thresholdAmount, BigDecimal discountAmount,
            BigDecimal discountRate
    ) {}
}
