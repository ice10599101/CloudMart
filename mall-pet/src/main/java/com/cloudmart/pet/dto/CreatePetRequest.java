package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 领养宠物请求。外观/性格走白名单校验（服务端权威，越界值 400）。
 */
@Schema(description = "领养宠物请求")
public record CreatePetRequest(
        @Schema(description = "宠物名（1-12 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请给宠物取个名字")
        @Size(max = 12, message = "宠物名最长 12 个字符")
        String name,

        @Schema(description = "种类: CAT/DOG/RABBIT/FOX/PANDA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择宠物种类")
        @Pattern(regexp = "CAT|DOG|RABBIT|FOX|PANDA", message = "宠物种类非法")
        String species,

        @Schema(description = "性别: MALE/FEMALE（缺省 MALE，兼容旧客户端）")
        @Pattern(regexp = "MALE|FEMALE", message = "宠物性别非法")
        String gender,

        @Schema(description = "外观颜色: orange/gray/white/brown/pink")
        @Pattern(regexp = "orange|gray|white|brown|pink", message = "外观颜色非法")
        String color,

        @Schema(description = "配饰: none/bell/bowtie/glasses/scarf")
        @Pattern(regexp = "none|bell|bowtie|glasses|scarf", message = "配饰非法")
        String accessory,

        @Schema(description = "性格: LIVELY/GENTLE/TSUNDERE/SIMPLE/COOL/CHATTERBOX", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择宠物性格")
        @Pattern(regexp = "LIVELY|GENTLE|TSUNDERE|SIMPLE|COOL|CHATTERBOX", message = "性格非法")
        String personality
) {
}
