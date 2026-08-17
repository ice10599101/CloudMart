package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 我的心愿摘要项 VO（嵌套于 {@link HomeAggregationVO}）。
 *
 * <p>对应文档 2.18 myWishes 数组元素，仅展示首页摘要字段。
 * 完整列表走 GET /wish/my/wishes。</p>
 *
 * @param wishId    心愿 ID
 * @param title     心愿标题
 * @param status    心愿状态
 * @param progress  完成百分比（0-100）
 * @param fruitType 果实类型
 */
@Schema(name = "MyWishSummaryVO", description = "我的心愿摘要项")
public record MyWishSummaryVO(
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "心愿状态") WishStatus status,
        @Schema(description = "完成百分比 0-100") Integer progress,
        @Schema(description = "果实类型") FruitType fruitType
) {}
