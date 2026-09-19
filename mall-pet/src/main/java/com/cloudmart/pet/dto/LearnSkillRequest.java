package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 技能学习请求（需背包中已有对应技能书；学习幂等，重复学习返回冲突提示）。
 */
@Schema(description = "宠物技能学习请求")
public record LearnSkillRequest(
        @Schema(description = "技能编码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择要学习的技能")
        String skillCode
) {
}
