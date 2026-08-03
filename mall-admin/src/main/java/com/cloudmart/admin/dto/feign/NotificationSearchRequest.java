package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通知搜索请求，与 mall-notification 服务端 AdminNotificationController 查询参数对齐
 */
public record NotificationSearchRequest(
    Long userId,
    String type,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public NotificationSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
