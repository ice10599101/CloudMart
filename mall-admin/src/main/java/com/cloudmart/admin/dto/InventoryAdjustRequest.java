package com.cloudmart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * TRADE-02：盘点调整请求（管理后台入参）。
 * <p>BASE-01：skuId 为十进制字符串（雪花 ID 防精度损失），由 Jackson 解析为 Long；
 * delta 非 0、原因必填、乐观锁版本 CAS、幂等键必填。</p>
 */
@Schema(description = "库存调整请求（盘点调整单）")
public record InventoryAdjustRequest(
    @Schema(description = "SKU ID（十进制字符串）")
    @NotBlank @Pattern(regexp = "^\\d{1,19}$", message = "SKU ID 应为 1-19 位数字")
    String skuId,

    @Schema(description = "调整量：正盘盈/负盘亏，非 0")
    @NotNull(message = "调整量不能为空")
    Integer delta,

    @Schema(description = "调整原因")
    @NotBlank(message = "调整原因不能为空")
    @Size(max = 255, message = "调整原因最长 255 字符")
    String reason,

    @Schema(description = "提交时期望的乐观锁版本")
    @NotNull(message = "版本号不能为空")
    Integer expectedVersion,

    @Schema(description = "幂等键（客户端生成）")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键最长 64 字符")
    String requestKey
) {
}
