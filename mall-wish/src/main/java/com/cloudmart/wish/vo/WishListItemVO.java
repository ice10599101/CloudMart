package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿列表项 VO（对应文档 2.1 GET /wish/wishes cursor 分页响应元素）。
 *
 * <p>仅包含列表展示必要字段，详情字段（成长记录、打卡天数、进度）见 {@link WishVO}。</p>
 *
 * @param id             心愿 ID（同时作为 cursor 游标，按 created_at 倒序时游标即 id）
 * @param title          心愿标题
 * @param description    心愿描述（列表场景可由前端截断展示）
 * @param mediaUrls      媒体资源 URL 列表（OSS 完整 URL，由 Service 层拼接）
 * @param categoryId     分类 ID
 * @param categoryName   分类名称（冗余字段，避免 N+1 查询 wish_category）
 * @param tags           标签列表（最多 5 个）
 * @param visibility     可见性
 * @param status         心愿状态
 * @param fruitType      果实类型
 * @param authorId       作者用户 ID
 * @param authorNickname 作者昵称
 * @param authorAvatar   作者头像 URL
 * @param lightCount     点亮数
 * @param sameWishCount  同求数
 * @param blessCount     祝福数
 * @param supportCount   总互动数（生成列：light + sameWish + bless）
 * @param commentCount   评论数（Feign mall-community 填充，失败降级为 0）
 * @param expectedAt     预计完成时间（可空）
 * @param createdAt      创建时间（UTC，前端按用户时区渲染）
 * @param updatedAt      更新时间（UTC）
 */
@Schema(name = "WishListItemVO", description = "心愿列表项")
public record WishListItemVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "心愿标题") String title,
        @Schema(description = "心愿描述") String description,
        @Schema(description = "媒体资源 URL 列表") List<String> mediaUrls,
        @Schema(description = "分类 ID") Long categoryId,
        @Schema(description = "分类名称") String categoryName,
        @Schema(description = "标签列表") List<String> tags,
        @Schema(description = "可见性") WishVisibility visibility,
        @Schema(description = "心愿状态") WishStatus status,
        @Schema(description = "果实类型") FruitType fruitType,
        @Schema(description = "作者用户 ID") Long authorId,
        @Schema(description = "作者昵称") String authorNickname,
        @Schema(description = "作者头像 URL") String authorAvatar,
        @Schema(description = "点亮数") Integer lightCount,
        @Schema(description = "同求数") Integer sameWishCount,
        @Schema(description = "祝福数") Integer blessCount,
        @Schema(description = "总互动数") Integer supportCount,
        @Schema(description = "评论数") Integer commentCount,
        @Schema(description = "预计完成时间") LocalDateTime expectedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
