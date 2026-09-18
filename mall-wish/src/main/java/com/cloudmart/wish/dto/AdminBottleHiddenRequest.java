package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理端漂流瓶下架/恢复请求。
 *
 * @param isHidden 是否下架（true 用户端不可见，数据保留；false 恢复展示）
 */
@Schema(description = "管理端漂流瓶下架/恢复请求")
public record AdminBottleHiddenRequest(
        @Schema(description = "是否下架（true 下架 / false 恢复）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "是否下架不能为空")
        Boolean isHidden
) {
}
