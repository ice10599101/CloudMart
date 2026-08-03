package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupons")
@Tag(name = "优惠券内部接口", description = "供 XXL-JOB 等内部系统调用的优惠券接口")
public class CouponInternalController {

    private final CouponService couponService;

    public CouponInternalController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/expire-batch")
    @Operation(summary = "批量过期优惠券", description = "将所有已过期的 UNUSED 状态用户优惠券批量标记为 EXPIRED，由 XXL-JOB 定时调用")
    public ApiResponse<Integer> expireBatch() {
        int count = couponService.expireBatch();
        return ApiResponse.ok(count);
    }
}
