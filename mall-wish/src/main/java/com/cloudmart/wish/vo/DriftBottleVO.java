package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 漂流瓶（匿名化）：不含投瓶人 userId/昵称/头像。
 *
 * @param bottleId  漂流瓶 ID
 * @param content   自由匿名文字（关联心愿时为空）
 * @param wishId    关联心愿 ID（自由文字时为 null）
 * @param wishTitle 关联心愿标题（自由文字时为 null）
 * @param wishTags  关联心愿标签
 * @param status    FLOATING / PICKED
 * @param role      THROWN（我投出的）/ PICKED（我捞到的）
 * @param thrownAt  投瓶时间
 * @param pickedAt  捞起时间（未捞起为 null）
 */
@Schema(description = "漂流瓶（匿名）")
public record DriftBottleVO(
        Long bottleId,
        String content,
        Long wishId,
        String wishTitle,
        List<String> wishTags,
        String status,
        String role,
        LocalDateTime thrownAt,
        LocalDateTime pickedAt
) {
}