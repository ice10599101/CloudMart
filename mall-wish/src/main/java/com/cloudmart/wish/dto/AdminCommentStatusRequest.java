package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理后台评论上下架请求（Sprint 1.2）。
 *
 * @param status 目标状态：HIDDEN=下架（四端立即不展示），VISIBLE=恢复上架
 */
@Schema(description = "评论上下架请求")
public record AdminCommentStatusRequest(
        @Schema(description = "目标状态", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"VISIBLE", "HIDDEN"})
        @NotNull(message = "目标状态不能为空")
        WishCommentStatus status
) {
}
