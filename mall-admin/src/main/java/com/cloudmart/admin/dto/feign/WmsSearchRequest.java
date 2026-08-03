package com.cloudmart.admin.dto.feign;

public record WmsSearchRequest(
    String status,
    Long warehouseId,
    Integer page,
    Integer size
) {
    public WmsSearchRequest {
        if (page == null) page = 1;
        if (size == null) size = 10;
    }
}
