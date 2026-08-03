package com.cloudmart.admin.dto.feign;

public record LiveRoomSearchRequest(
    String status,
    Integer page,
    Integer size
) {
    public LiveRoomSearchRequest {
        if (page == null) page = 1;
        if (size == null) size = 10;
    }
}
