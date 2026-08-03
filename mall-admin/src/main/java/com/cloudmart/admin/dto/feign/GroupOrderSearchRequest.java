package com.cloudmart.admin.dto.feign;

public record GroupOrderSearchRequest(
    Long activityId,
    String status,
    Integer page,
    Integer size
) {
    public GroupOrderSearchRequest {
        if (page == null) page = 1;
        if (size == null) size = 10;
    }
}
