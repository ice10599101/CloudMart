package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 今日推荐心愿项 VO（嵌套于 {@link HomeAggregationVO}）。
 *
 * <p>对应文档 2.18 todayRecommend 数组元素，展示轻量字段。
 * 完整详情走 GET /wish/wishes/{id}。</p>
 *
 * @param wishId          心愿 ID
 * @param title           心愿标题
 * @param coverUrl        封面图 URL（取 mediaUrls 第一张，无则 null）
 * @param authorNickname  作者昵称
 * @param supportCount    总互动数（用于推荐算法展示）
 * @param fruitType       果实类型
 */
@Schema(name = "TodayRecommendItemVO", description = "今日推荐心愿项")
public record TodayRecommendItemVO(
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "封面图 URL") String coverUrl,
        @Schema(description = "作者昵称") String authorNickname,
        @Schema(description = "总互动数") Integer supportCount,
        @Schema(description = "果实类型") FruitType fruitType
) {}
