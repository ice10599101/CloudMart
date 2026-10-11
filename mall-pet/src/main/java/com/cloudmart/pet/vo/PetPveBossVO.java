package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Boss 配置（6.2 R03：动态下发，前端不硬编码 HP/数值）。
 */
@Schema(description = "PVE Boss 配置")
public record PetPveBossVO(
        @Schema(description = "Boss 编码") String bossCode,
        @Schema(description = "Boss 名称") String name,
        @Schema(description = "最大 HP") int maxHp,
        @Schema(description = "攻击力") int attack,
        @Schema(description = "防御力") int defense
) {
}
