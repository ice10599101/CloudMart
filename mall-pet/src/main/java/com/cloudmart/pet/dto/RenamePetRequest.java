package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 宠物改名请求（30 天一次，冷却在服务端校验）。
 */
@Schema(description = "宠物改名请求")
public record RenamePetRequest(
        @Schema(description = "新名字（1-12 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请填写新名字")
        @Size(max = 12, message = "宠物名最长 12 个字符")
        String name
) {
}
