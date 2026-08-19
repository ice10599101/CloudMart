package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 取消互动结果 VO（文档 2.2 节）。
 *
 * @param id      互动记录 ID
 * @param type    互动类型
 * @param revoked 是否已撤销（恒为 true）
 */
@Schema(description = "取消互动结果")
public record InteractionRevokeVO(
        @Schema(description = "互动记录 ID") Long id,
        @Schema(description = "互动类型") InteractionType type,
        @Schema(description = "是否已撤销") boolean revoked
) {
}
