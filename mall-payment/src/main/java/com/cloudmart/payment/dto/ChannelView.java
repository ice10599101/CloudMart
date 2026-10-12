package com.cloudmart.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * R09 渠道可用性视图（三端收银台渠道列表唯一来源）：不包含任何密钥/凭据信息。
 *
 * @param channel     渠道编码（创建尝试时原样回传）
 * @param name        展示名
 * @param action      该场景收单动作：REDIRECT/QR/SDK/NONE
 * @param scene       查询场景：H5/MINIAPP/APP
 * @param maintenance 是否维护中（展示但禁用选择）
 */
@Schema(description = "R09 可用支付渠道视图")
public record ChannelView(
        @Schema(description = "渠道编码") String channel,
        @Schema(description = "展示名") String name,
        @Schema(description = "收单动作：REDIRECT/QR/SDK/NONE") String action,
        @Schema(description = "场景：H5/MINIAPP/APP") String scene,
        @Schema(description = "维护中（置灰不可选）") boolean maintenance) {
}
