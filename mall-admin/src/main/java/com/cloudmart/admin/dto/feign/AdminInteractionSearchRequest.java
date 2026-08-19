package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 互动记录管理搜索请求，与 mall-wish 服务端 AdminInteractionListQuery 查询参数对齐。
 *
 * <p>type/startTime/endTime 以字符串透传，由 mall-wish 端做反序列化校验；
 * 时间格式 ISO 8601（如 2026-08-18T00:00:00）。</p>
 */
public record AdminInteractionSearchRequest(
    Long wishId,
    Long userId,
    String type,
    String startTime,
    String endTime,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminInteractionSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
