package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 捞瓶人匿名开关请求。
 *
 * @param isAnonymous 是否匿名（true 匿名隐藏捞瓶人身份 / false 实名，投瓶人可见捞瓶人身份）
 */
@Schema(description = "捞瓶人匿名开关请求")
public record PickerAnonymityRequest(
        @Schema(description = "是否匿名（默认 true；false 实名时投瓶人可见捞瓶人身份）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "是否匿名不能为空")
        Boolean isAnonymous
) {
}
