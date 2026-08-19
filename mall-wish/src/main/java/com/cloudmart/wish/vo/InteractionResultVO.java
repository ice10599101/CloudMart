package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 互动结果 VO（文档 2.2 节）。
 *
 * @param id             互动记录 ID
 * @param type           互动类型
 * @param lightCount     心愿累计点亮数（本次互动后）
 * @param sameWishCount  心愿累计同求数（本次互动后）
 * @param blessCount     心愿累计祝福数（本次互动后）
 * @param starlightCost  本次互动消耗星光数（LIGHT=2，其余 0）
 */
@Schema(description = "互动结果")
public record InteractionResultVO(
        @Schema(description = "互动记录 ID") Long id,
        @Schema(description = "互动类型") InteractionType type,
        @Schema(description = "累计点亮数") Integer lightCount,
        @Schema(description = "累计同求数") Integer sameWishCount,
        @Schema(description = "累计祝福数") Integer blessCount,
        @Schema(description = "本次消耗星光数") int starlightCost
) {
}
