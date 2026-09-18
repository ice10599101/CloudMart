package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 漂流瓶每日配额（投瓶 10 个/天、打捞 20 次/天，UTC 自然日）。
 *
 * @param throwUsed 今日已投瓶数
 * @param throwLimit 每日投瓶上限
 * @param fishUsed 今日已打捞次数
 * @param fishLimit 每日打捞上限
 */
@Schema(description = "漂流瓶每日配额")
public record DriftBottleQuotaVO(
        long throwUsed,
        int throwLimit,
        long fishUsed,
        int fishLimit
) {
}
