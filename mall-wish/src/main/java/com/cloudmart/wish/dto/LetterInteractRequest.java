package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 信笺匿名互动请求（Sprint 3.3，文档 2.10：type=BLESS/LIGHT）。
 */
@Schema(description = "信笺匿名互动请求")
public record LetterInteractRequest(
        @Schema(description = "互动类型：BLESS 匿名祝福 / LIGHT 点亮对方心愿", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "互动类型不能为空")
        String type,

        @Schema(description = "互动附言（可选，仅本人可见回显）")
        @Size(max = 200, message = "附言最长 200 字")
        String content
) {
}
