package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "兑换码VO")
public record ExchangeCodeVO(

    @Schema(description = "兑换码ID")
    Long id,

    @Schema(description = "兑换码")
    String code,

    @Schema(description = "关联的优惠券模板ID")
    Long templateId,

    @Schema(description = "状态: UNUSED-未兑换, EXCHANGED-已兑换, DISABLED-已作废")
    String status,

    @Schema(description = "兑换用户ID")
    Long userId,

    @Schema(description = "兑换时间")
    LocalDateTime exchangedAt,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {

    /**
     * 批量生成响应
     */
    @Schema(description = "批量生成兑换码响应")
    public record BatchGenerateResult(

        @Schema(description = "批次号")
        String batchNo,

        @Schema(description = "生成的兑换码列表")
        List<String> codes,

        @Schema(description = "生成数量")
        int count,

        @Schema(description = "关联的优惠券模板ID")
        Long templateId
    ) {}
}
