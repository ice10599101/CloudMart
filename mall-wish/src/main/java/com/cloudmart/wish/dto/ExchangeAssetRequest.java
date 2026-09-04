package com.cloudmart.wish.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 工坊星光兑换请求（Sprint 3.6）。
 *
 * <p>assetId 声明为 Long：雪花 ID 经 JsSafeLongSerializer 以字符串形态下发到前端，
 * 前端回传 string / number 均由 Jackson 宽松反序列化为 Long，避免 Map 取值强转
 * ClassCastException（19 位雪花 ID 恒超 JS 安全整数范围，前端实际持有 string）。</p>
 */
public record ExchangeAssetRequest(
        @NotNull(message = "资产 ID 不能为空") Long assetId,
        String paymentMethod) {
}
