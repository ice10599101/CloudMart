package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 我的心愿列表项 VO（对应文档 2.6 GET /wish/my/wishes 响应元素）。
 *
 * <p>区别于公开列表 {@link WishListItemVO}：包含进度百分比，但不包含作者信息
 * （因为列表固定为当前用户自己的心愿）。</p>
 *
 * @param id          心愿 ID
 * @param title       心愿标题
 * @param status      心愿状态
 * @param fruitType   果实类型
 * @param progress    完成百分比（0-100）
 * @param lightCount  点亮数
 * @param createdAt   创建时间（UTC）
 */
@Schema(name = "MyWishListItemVO", description = "我的心愿列表项")
public record MyWishListItemVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "心愿状态") WishStatus status,
        @Schema(description = "果实类型") FruitType fruitType,
        @Schema(description = "完成百分比 0-100") Integer progress,
        @Schema(description = "点亮数") Integer lightCount,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {}
