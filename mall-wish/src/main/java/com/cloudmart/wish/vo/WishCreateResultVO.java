package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 心愿创建结果 VO（对应文档 2.1 POST /wish/wishes 响应）。
 *
 * <p>创建成功后仅返回核心字段，前端可据此跳转详情页或刷新列表。</p>
 *
 * @param id        心愿 ID
 * @param title     心愿标题
 * @param status    初始状态（默认 ACTIVE）
 * @param fruitType 初始果实类型（默认 GLOW）
 * @param createdAt 创建时间（UTC）
 */
@Schema(name = "WishCreateResultVO", description = "心愿创建结果")
public record WishCreateResultVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "初始状态") WishStatus status,
        @Schema(description = "初始果实类型") FruitType fruitType,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {}
