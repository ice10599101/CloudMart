package com.cloudmart.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交问卷答卷请求。
 *
 * @param answers 每题答案（须覆盖全部必答题；重复提交覆盖更新）
 */
@Schema(description = "提交问卷答卷请求")
public record SurveyResponseRequest(
        @Schema(description = "答案列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "答案不能为空")
        @Valid
        List<SurveyAnswerInput> answers
) {

    @Schema(description = "单题答案")
    public record SurveyAnswerInput(
            @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "题目 ID 不能为空")
            Long questionId,

            @Schema(description = "选中选项 ID（选择题）")
            @Size(max = 10, message = "一次最多选择 10 个选项")
            List<Long> optionIds,

            @Schema(description = "填空内容（填空题）")
            @Size(max = 500, message = "填空内容不能超过 500 字符")
            String text
    ) {
    }
}
