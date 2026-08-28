package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 建组请求（Sprint 2.6，文档 2.8：POST /wish/match/groups）。
 *
 * @param keyword    组主题关键词（≤60 字）
 * @param maxMembers 容量（2-4，默认 4）
 * @param wishId     关联心愿 ID（可空）
 */
@Schema(description = "同愿小组建组请求")
public record CreateMatchGroupRequest(
        @Schema(description = "组主题关键词", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "关键词不能为空")
        @Size(max = 60, message = "关键词最长 60 字")
        String keyword,

        @Schema(description = "小组容量（2-4）")
        @Min(value = 2, message = "小组容量至少 2 人")
        @Max(value = 4, message = "小组容量最多 4 人")
        Integer maxMembers,

        @Schema(description = "关联心愿 ID（可选）")
        Long wishId
) {
}
