package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * C19：收货确认统一请求体。后台确认时生成并保留稳定 receiptId——双击/超时重试/重启恢复
 * 必须携带同一收货事实身份，服务端按 receiptId 幂等收敛。qualityResult 缺省合格入库。
 */
@Schema(description = "收货确认请求")
public record ReceiveItemRequest(
        @Schema(description = "入库明细 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "itemId 不能为空")
        Long itemId,

        @Schema(description = "本次实收数量（正整数）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "receivedQuantity 不能为空")
        @Min(value = 1, message = "收货数量必须为正数")
        Integer receivedQuantity,

        @Schema(description = "收货流水 ID（后台生成，重试必须原样回传）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "receiptId 不能为空")
        @Size(max = 64, message = "receiptId 最长 64 字符")
        String receiptId,

        @Schema(description = "质检结果：PASSED 合格（可售）/ QUARANTINE 隔离（不计可售）；缺省 PASSED")
        String qualityResult
) {
}
