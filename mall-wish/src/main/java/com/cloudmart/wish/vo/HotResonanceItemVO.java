package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 热门共鸣心愿项 VO（嵌套于 {@link HomeAggregationVO}）。
 *
 * <p>对应文档 2.18 hotResonance 数组元素，复用 {@code wish:hot:feed} ZSet Top 5。</p>
 *
 * @param wishId      心愿 ID
 * @param title       心愿标题
 * @param supportCount 总互动数
 */
@Schema(name = "HotResonanceItemVO", description = "热门共鸣心愿项")
public record HotResonanceItemVO(
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "总互动数") Integer supportCount
) {}
