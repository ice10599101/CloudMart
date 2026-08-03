package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(contextId = "orderCouponFeignClient", name = "mall-coupon", path = "/user-coupons", fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    @PostMapping("/use")
    ApiResponse<Void> useCoupon(@RequestBody UseCouponRequest request);

    @PostMapping("/return")
    ApiResponse<Void> returnCoupon(@RequestBody ReturnCouponRequest request);

    @GetMapping("/{id}")
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
