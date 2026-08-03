package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 支付搜索请求，与 mall-payment 服务端 AdminPaymentController 查询参数对齐
 */
public record PaymentSearchRequest(
    String status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public PaymentSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
