package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 商城购买请求（只传"买什么"，价格与加成由服务端配置决定——客户端不能携带数值）。
 *
 * <p>R02：petId/expectedConfigVersion 为新版可选字段——缺省 petId 时服务端在首次执行时
 * 绑定当前主宠并冻结，重放按原归属；expectedConfigVersion 用于价格版本冲突检测（409）。</p>
 */
@Schema(description = "宠物商城购买请求")
public record BuyItemRequest(
        @Schema(description = "物品类型: EQUIPMENT/SKIN/SKILL_BOOK/FOOD", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择物品类型")
        @Pattern(regexp = "EQUIPMENT|SKIN|SKILL_BOOK|FOOD", message = "物品类型非法")
        String itemType,

        @Schema(description = "物品编码（商城列表返回的 code）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择要购买的物品")
        String itemCode,

        @Schema(description = "目标宠物 ID（可选；缺省绑定当前主宠并在首次执行时冻结）")
        Long petId,

        @Schema(description = "期望的商品配置版本（可选；不匹配返回 409 PET_CONFIG_VERSION_CONFLICT）")
        String expectedConfigVersion
) {
}
