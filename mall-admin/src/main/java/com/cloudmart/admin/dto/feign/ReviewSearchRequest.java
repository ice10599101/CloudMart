package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 评价搜索请求，与 mall-product 服务端 AdminReviewController 查询参数对齐
 */
public record ReviewSearchRequest(
    Long productId,
    Integer status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public ReviewSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
