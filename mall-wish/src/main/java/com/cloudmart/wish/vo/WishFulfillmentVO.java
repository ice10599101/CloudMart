package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 还愿详情 VO（文档 2.4 节 GET /wish/wishes/{id}/fulfillment）。
 *
 * @param id              还愿记录 ID
 * @param wishId          心愿 ID
 * @param story           还愿故事（已转义存储，可直接渲染）
 * @param mediaUrls       完成照片/视频 URL 列表
 * @param feeling         感悟
 * @param authorId        作者用户 ID
 * @param authorNickname  作者昵称（Feign 获取，降级为占位值）
 * @param authorAvatar    作者头像
 * @param createdAt       提交时间
 */
@Schema(description = "还愿详情")
public record WishFulfillmentVO(
        @Schema(description = "还愿记录 ID") Long id,
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "还愿故事") String story,
        @Schema(description = "完成照片/视频 URL 列表") List<String> mediaUrls,
        @Schema(description = "感悟") String feeling,
        @Schema(description = "作者用户 ID") Long authorId,
        @Schema(description = "作者昵称") String authorNickname,
        @Schema(description = "作者头像") String authorAvatar,
        @Schema(description = "提交时间") LocalDateTime createdAt
) {
}
