package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 优惠券搜索请求，与 mall-coupon 服务端 AdminCouponController 查询参数对齐
 */
public record CouponSearchRequest(
    String type,
    String status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public CouponSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
