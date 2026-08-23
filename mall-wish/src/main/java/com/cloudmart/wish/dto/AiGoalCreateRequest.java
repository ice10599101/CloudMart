package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 拆解目标勾选持久化请求（用户在拆解结果中勾选步骤后提交，
 * 文档 2.11：勾选步骤持久化到 ㊱b wish_ai_goal，status=PENDING）。
 *
 * @param sessionId  AI 会话 ID（拆解响应返回，关联对话记录）
 * @param wishId     关联心愿 ID（可选）
 * @param goals      勾选的步骤列表（原样回传拆解结果中的 goal）
 */
@Schema(description = "AI 拆解目标勾选持久化请求")
public record AiGoalCreateRequest(
        @Schema(description = "AI 会话 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "会话 ID 不能为空")
        @Size(max = 64, message = "会话 ID 最长 64 字符")
        String sessionId,

        @Schema(description = "关联心愿 ID（可选）")
        Long wishId,

        @Schema(description = "勾选的步骤列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "至少勾选一个步骤")
        @Valid
        List<GoalItem> goals
) {

    /**
     * 勾选步骤项（来自拆解结果的 goal 原样回传）。
     */
    @Schema(description = "勾选步骤项")
    public record GoalItem(
            @Schema(description = "步骤标题", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "步骤标题不能为空")
            @Size(max = 100, message = "步骤标题最长 100 字")
            String title,

            @Schema(description = "步骤描述", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "步骤描述不能为空")
            String description,

            @Schema(description = "预计完成天数（1-365）")
            Integer estimatedDays,

            @Schema(description = "优先级（1-5，1 最高）")
            Integer priority
    ) {
    }
}
