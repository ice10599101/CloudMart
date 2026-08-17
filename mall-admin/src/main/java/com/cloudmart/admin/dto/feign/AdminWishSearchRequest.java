package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 心愿管理搜索请求，与 mall-wish 服务端 AdminWishListQuery 查询参数对齐。
 *
 * <p>枚举值（status/auditStatus/visibility）以字符串透传，由 mall-wish 端做反序列化校验。</p>
 */
public record AdminWishSearchRequest(
    Long userId,
    Long categoryId,
    String status,
    String auditStatus,
    String visibility,
    String keyword,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminWishSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
