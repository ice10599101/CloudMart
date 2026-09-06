package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台心愿视图 VO（包含审核相关字段，对应管理后台心愿详情）。
 *
 * <p>区别于用户端 {@link WishVO}，此 VO 包含：</p>
 * <ul>
 *   <li>{@code auditStatus}：审核状态</li>
 *   <li>{@code auditStrategy}：审核策略</li>
 *   <li>{@code isVisible}：是否对用户可见（与审核状态解耦）</li>
 *   <li>{@code deletedAt}：软删时间（用于审计追溯）</li>
 * </ul>
 */
@Schema(name = "AdminWishVO", description = "管理后台心愿视图")
public record AdminWishVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "作者用户 ID") Long userId,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "心愿描述") String description,
        @Schema(description = "媒体资源 URL 列表") List<String> mediaUrls,
        @Schema(description = "分类 ID") Long categoryId,
        @Schema(description = "分类名称") String categoryName,
        @Schema(description = "标签列表") List<String> tags,
        @Schema(description = "可见性") WishVisibility visibility,
        @Schema(description = "心愿状态") WishStatus status,
        @Schema(description = "果实类型") FruitType fruitType,
        @Schema(description = "审核状态") AuditStatus auditStatus,
        @Schema(description = "审核策略") AuditStrategy auditStrategy,
        @Schema(description = "是否对用户可见") Boolean isVisible,
        @Schema(description = "是否管理端置顶") Boolean isTop,
        @Schema(description = "点亮数") Integer lightCount,
        @Schema(description = "同求数") Integer sameWishCount,
        @Schema(description = "祝福数") Integer blessCount,
        @Schema(description = "总互动数") Integer supportCount,
        @Schema(description = "预计完成时间") LocalDateTime expectedAt,
        @Schema(description = "实际还愿时间") LocalDateTime fulfilledAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "软删除时间（null 表示未删除）") LocalDateTime deletedAt
) {}
