package com.cloudmart.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * T10：满减活动发布前试算请求。
 *
 * <p>两种入参二选一：{@code items}（SKU+数量，由服务端取权威价计算购物篮金额，
 * 会校验 SKU 存在/在售/数量合法）或 {@code orderAmount}（后台已有的“金额试算”模式）。
 * 同时提供时以 items 为准。{@code previewAt} 为空时取当前时间。</p>
 */
public record TieredPreviewRequest(
        @Valid List<PreviewItem> items,
        BigDecimal orderAmount,
        LocalDateTime previewAt) {

    public record PreviewItem(
            @NotNull(message = "SKU ID 不能为空") Long skuId,
            @NotNull(message = "数量不能为空")
            @Min(value = 1, message = "数量必须大于 0")
            @Max(value = 999, message = "单行数量不能超过 999") Integer quantity) {
    }
}
