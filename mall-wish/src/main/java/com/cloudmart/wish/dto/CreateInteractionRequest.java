package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起互动请求（文档 2.2 节）。
 *
 * @param type    互动类型：LIGHT/SAME_WISH/BLESS/ANON_STAR（匿名星光 Sprint 2.6 启用）
 * @param content 祝福文字内容（仅 BLESS 必填，≤200 字符，入库前 XSS 转义）
 */
@Schema(description = "发起互动请求")
public record CreateInteractionRequest(
        @Schema(description = "互动类型", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"LIGHT", "SAME_WISH", "BLESS", "ANON_STAR"})
        @NotNull(message = "互动类型不能为空")
        InteractionType type,

        @Schema(description = "祝福内容（仅 BLESS 必填，最多 200 字符）")
        @Size(max = 200, message = "祝福内容不能超过 200 字符")
        String content
) {
}
