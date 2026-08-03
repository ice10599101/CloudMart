package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * 商品搜索请求，与 mall-product 服务端 ProductSearchRequest 字段对齐
 */
public record ProductSearchRequest(
    String keyword,
    Long categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String sort,
    Integer status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer size
) {
    public ProductSearchRequest {
        if (page == null) { page = 1; }
        if (size == null) { size = 20; }
        if (sort == null || sort.isBlank()) { sort = "created"; }
    }
}
