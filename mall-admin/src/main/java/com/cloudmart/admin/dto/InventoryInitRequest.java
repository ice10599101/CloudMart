package com.cloudmart.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 库存初始化请求（BASE-01）。
 *
 * <p>身份 ID 由 Jackson 从十进制字符串解析为 Long：前端雪花 ID 以字符串提交，
 * 禁止 {@code ((Number) body.get("skuId")).longValue()} 之类的 Map 强转——
 * 字符串入参会 ClassCastException，数字入参在浏览器端已丢精度。
 */
public record InventoryInitRequest(
        @NotNull(message = "skuId 不能为空") Long skuId,
        @NotNull(message = "productId 不能为空") Long productId,
        @NotNull(message = "stock 不能为空") @Min(value = 0, message = "stock 不能为负") Integer stock) {
}
