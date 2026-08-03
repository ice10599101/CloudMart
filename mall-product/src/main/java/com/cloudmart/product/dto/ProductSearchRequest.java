package com.cloudmart.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record ProductSearchRequest(
    String keyword,
    Long categoryId,
    String brand,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String sort,
    Integer status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer size
) {
    public ProductSearchRequest {
        if (page == null) {
            page = 1;
        }
        if (size == null) {
            size = 20;
        }
        if (sort == null || sort.isBlank()) {
            sort = "relevance";
        }
    }
}
