package com.cloudmart.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 投票请求。
 *
 * @param optionIds 选中选项 ID（单选恰好 1 个；多选 1-10 个）
 */
@Schema(description = "投票请求")
public record PollVoteRequest(
        @Schema(description = "选中选项 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "请选择选项")
        @Size(max = 10, message = "一次最多选择 10 个选项")
        List<Long> optionIds
) {
}
