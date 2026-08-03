package com.cloudmart.admin.dto.feign;

public record TieredPromotionSearchRequest(
    String status,
    Integer page,
    Integer size
) {
    public TieredPromotionSearchRequest {
        if (page == null) page = 1;
        if (size == null) size = 10;
    }
}
