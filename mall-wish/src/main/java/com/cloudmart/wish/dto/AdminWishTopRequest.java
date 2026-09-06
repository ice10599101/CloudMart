package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理后台心愿置顶请求（对齐帖子管理模式）。
 *
 * @param isTop true=置顶（用户端广场优先展示）/ false=取消置顶
 */
@Schema(name = "AdminWishTopRequest", description = "管理后台心愿置顶请求")
public record AdminWishTopRequest(
        @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "isTop 不能为空")
        Boolean isTop
) {
}
