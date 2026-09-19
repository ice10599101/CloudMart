package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 宠物关系申请请求（三期）。 */
@Schema(description = "宠物关系申请")
public record RequestRelationRequest(
        @Schema(description = "对方宠物 ID（候选列表返回的 petId）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择对方的宠物")
        Long toPetId,

        @Schema(description = "关系类型: COUPLE/BESTIE/BROTHER/CONFIDANT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择关系类型")
        @Pattern(regexp = "COUPLE|BESTIE|BROTHER|CONFIDANT", message = "关系类型非法")
        String relType,

        @Schema(description = "申请留言（可选，40 字以内）")
        @Size(max = 40, message = "申请留言最多 40 字")
        String message
) {
}
