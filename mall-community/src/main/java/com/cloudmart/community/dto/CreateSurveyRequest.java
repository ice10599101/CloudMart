package com.cloudmart.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建问卷请求（编辑器附件，V10）。
 */
@Schema(description = "创建问卷请求")
public record CreateSurveyRequest(
        @Schema(description = "客户端 UUID（幂等主键）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "问卷 ID 不能为空")
        @Size(max = 36, message = "问卷 ID 长度非法")
        String id,

        @Schema(description = "宿主内容类型", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"POST", "WISH", "CAPSULE", "LETTER"})
        @NotBlank(message = "宿主内容类型不能为空")
        @Pattern(regexp = "POST|WISH|CAPSULE|LETTER", message = "宿主内容类型非法")
        String targetType,

        @Schema(description = "宿主内容 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "宿主内容 ID 不能为空")
        @Size(max = 64, message = "宿主内容 ID 长度非法")
        String targetId,

        @Schema(description = "问卷标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "问卷标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字符")
        String title,

        @Schema(description = "题目列表（1-10 题）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "题目不能为空")
        @Size(min = 1, max = 10, message = "题目数量须为 1-10 题")
        @Valid
        List<SurveyQuestionInput> questions
) {

    /**
     * 题目输入。
     *
     * @param text     题干
     * @param type     题型: single/multi/text
     * @param options  选项（选择题 2-10 个，填空题为空）
     * @param required 是否必答
     */
    @Schema(description = "问卷题目输入")
    public record SurveyQuestionInput(
            @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "题干不能为空")
            @Size(max = 500, message = "题干不能超过 500 字符")
            String text,

            @Schema(description = "题型", requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"single", "multi", "text"})
            @NotBlank(message = "题型不能为空")
            @Pattern(regexp = "single|multi|text", message = "题型非法")
            String type,

            @Schema(description = "选项（选择题 2-10 个）")
            @Size(max = 10, message = "选项不能超过 10 个")
            List<@NotBlank(message = "选项文本不能为空") @Size(max = 200, message = "选项不能超过 200 字符") String> options,

            @Schema(description = "是否必答")
            Boolean required
    ) {
    }
}
