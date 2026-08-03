package com.cloudmart.admin.dto.feign;

public record GroupActivitySearchRequest(
    String status,
    Integer page,
    Integer size
) {
    public GroupActivitySearchRequest {
        if (page == null) page = 1;
        if (size == null) size = 10;
    }
}
