package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.AuditStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 管理后台审核心愿请求 DTO。
 *
 * <p>对应 API: PUT /wish/admin/wishes/{id}/audit</p>
 */
public record AdminAuditWishRequest(

        @NotNull(message = "审核状态不能为空")
        AuditStatus auditStatus,

        String rejectReason
) {}
