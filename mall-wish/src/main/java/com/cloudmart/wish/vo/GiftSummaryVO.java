package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 我的礼物资产总览 VO。
 *
 * <p>「礼物资产」语义：礼物为即时消费（送礼即扣星光、收礼为荣誉凭证），
 * 资产总览 = 送收两个方向的累计件数与累计星光，不构成可转让的库存资产。
 * 当前星光余额经 /wish/my/resources 查询（前端同页展示）。</p>
 *
 * @param sentCount         累计送出件数（Σ count，含同一次多件）
 * @param sentStarlight     累计送出消耗星光（Σ total_price）
 * @param receivedCount     累计收到件数（Σ count）
 * @param receivedStarlight 累计收到星光价值（Σ total_price）
 */
@Schema(description = "我的礼物资产总览")
public record GiftSummaryVO(
        Long sentCount,
        Long sentStarlight,
        Long receivedCount,
        Long receivedStarlight
) {
}
