package com.cloudmart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * TRADE-02：盘点调整请求。库存唯一受控变更入口——初始化仅一次、补货走入库单，
 * 一切修正必须走本请求：delta 非 0、原因必填、乐观锁版本 CAS、幂等键防重复提交。
 */
@Schema(description = "库存调整请求（盘点调整单）")
public record InventoryAdjustRequest(
    @Schema(description = "SKU ID") @NotNull Long skuId,
    @Schema(description = "调整量：正盘盈/负盘亏，非 0") @NotNull Integer delta,
    @Schema(description = "调整原因（盘点/损耗/纠错等）") @NotBlank @Size(max = 255) String reason,
    @Schema(description = "提交时期望的乐观锁版本（冲突即拒绝，防并发覆盖）") @NotNull Integer expectedVersion,
    @Schema(description = "幂等键（客户端生成；同键同参返回原单）") @NotBlank @Size(max = 64) String requestKey
) {
    public InventoryAdjustRequest {
        if (delta != null && delta == 0) {
            throw new IllegalArgumentException("调整量不能为 0");
        }
        if (requestKey != null) {
            requestKey = requestKey.trim();
        }
        if (reason != null) {
            reason = reason.trim();
        }
    }
}
