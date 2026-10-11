package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 兑换码权威事实（R01）：供 mall-pet 权益核验只读查询。
 * 仅内部服务令牌可达——状态与归属以本表为准，调用方不得信任客户端转述。
 */
@Schema(description = "兑换码权威事实（R01 内部核验）")
public record ExchangeCodeFactVO(
        @Schema(description = "兑换码记录 ID") Long codeId,
        @Schema(description = "兑换码") String code,
        @Schema(description = "状态: UNUSED-未兑换, EXCHANGED-已兑换, DISABLED-已作废") String status,
        @Schema(description = "兑换用户 ID（未兑换为 null）") Long userId,
        @Schema(description = "兑换时间") LocalDateTime exchangedAt,
        @Schema(description = "关联优惠券模板 ID") Long templateId
) {
}
