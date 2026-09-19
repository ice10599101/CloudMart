package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 家园家具项（三期）：既用于"已摆放"（带坐标），也用于"背包/商城"（坐标为 null）。
 */
@Schema(description = "家园家具项")
public record PetHomeItemVO(
        @Schema(description = "家具编码") String code,
        @Schema(description = "家具名") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "分类: WALL/FLOOR/FURNITURE/PLANT/TOY/BED") String category,
        @Schema(description = "分类中文名") String categoryLabel,
        @Schema(description = "图标") String icon,
        @Schema(description = "稀有度") String rarity,
        @Schema(description = "舒适度") Integer comfort,
        @Schema(description = "售价（星光）") Integer priceStarlight,
        @Schema(description = "购买所需等级") Integer requiredLevel,
        @Schema(description = "网格 X（已摆放时有值）") Integer posX,
        @Schema(description = "网格 Y（已摆放时有值）") Integer posY,
        @Schema(description = "是否已拥有") Boolean owned,
        @Schema(description = "是否可购买") Boolean eligible,
        @Schema(description = "不可购买原因（null = 可购买）") String lockReason,
        @Schema(description = "是否当前主题（墙纸/地板）") Boolean themeActive
) {
}
