package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 心愿设为星火永久收藏结果 VO（对应文档 2.3 POST /wish/wishes/{id}/spark 响应）。
 */
@Schema(name = "WishSparkVO", description = "星火永久收藏结果")
public record WishSparkVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "果实类型（设置成功后为 SPARK）") FruitType fruitType,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
