package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理后台心愿上下架请求（对齐帖子管理模式）。
 *
 * @param visible true=上架（用户端可见）/ false=下架（用户端不可见）
 */
@Schema(name = "AdminWishVisibilityRequest", description = "管理后台心愿上下架请求")
public record AdminWishVisibilityRequest(
        @Schema(description = "是否上架", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "visible 不能为空")
        Boolean visible
) {
}
