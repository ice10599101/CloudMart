package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 评论管理搜索请求，与 mall-wish 服务端 AdminCommentListQuery 查询参数对齐。
 *
 * <p>status 以字符串透传（VISIBLE/HIDDEN），由 mall-wish 端做反序列化校验。</p>
 */
public record AdminCommentSearchRequest(
    Long wishId,
    Long userId,
    Boolean sensitiveHit,
    String status,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminCommentSearchRequest {
        if (page == null) { page = 1; }
        if (pageSize == null) { pageSize = 20; }
    }
}
