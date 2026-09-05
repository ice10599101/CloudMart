package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿详情 VO（对应文档 2.1 GET /wish/wishes/{id} 详情响应）。
 *
 * <p>列表字段基础上额外包含成长记录、打卡天数、进度。成长记录默认取最近 10 条，
 * 完整时间轴通过 GET /wish/wishes/{id}/growth-records cursor 分页加载。</p>
 */
@Schema(name = "WishVO", description = "心愿详情")
public record WishVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "心愿描述") String description,
        @Schema(description = "媒体资源 URL 列表") List<String> mediaUrls,
        @Schema(description = "分类 ID") Long categoryId,
        @Schema(description = "分类名称") String categoryName,
        @Schema(description = "标签列表") List<String> tags,
        @Schema(description = "可见性") WishVisibility visibility,
        @Schema(description = "心愿状态") WishStatus status,
        @Schema(description = "审核状态（PENDING=审核中，作者可见提示）") AuditStatus auditStatus,
        @Schema(description = "果实类型") FruitType fruitType,
        @Schema(description = "作者用户 ID") Long authorId,
        @Schema(description = "作者昵称") String authorNickname,
        @Schema(description = "作者头像 URL") String authorAvatar,
        @Schema(description = "点亮数") Integer lightCount,
        @Schema(description = "同求数") Integer sameWishCount,
        @Schema(description = "祝福数") Integer blessCount,
        @Schema(description = "匿名星光数") Integer anonStarCount,
        @Schema(description = "总互动数") Integer supportCount,
        @Schema(description = "评论数") Integer commentCount,
        @Schema(description = "预计完成时间") LocalDateTime expectedAt,
        @Schema(description = "是否启用 AI 回复（树洞心愿）") Boolean enableAiReply,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "最近成长记录列表（默认 10 条）") List<WishGrowthRecordVO> growthRecords,
        @Schema(description = "累计打卡天数") Integer checkinDays,
        @Schema(description = "心愿进度") WishProgressVO progress
) {}
