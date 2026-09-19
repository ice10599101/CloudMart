package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 商城购买请求（只传"买什么"，价格与加成由服务端配置决定——客户端不能携带数值）。
 */
@Schema(description = "宠物商城购买请求")
public record BuyItemRequest(
        @Schema(description = "物品类型: EQUIPMENT/SKIN/SKILL_BOOK", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择物品类型")
        @Pattern(regexp = "EQUIPMENT|SKIN|SKILL_BOOK", message = "物品类型非法")
        String itemType,

        @Schema(description = "物品编码（商城列表返回的 code）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择要购买的物品")
        String itemCode
) {
}
