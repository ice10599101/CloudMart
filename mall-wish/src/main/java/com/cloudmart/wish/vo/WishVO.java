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
        @Schema(description = "关联商品 ID（心愿关联商品闭环，可空）") Long linkedProductId,
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
        @Schema(description = "心愿进度") WishProgressVO progress,

        @Schema(description = "T22：可申诉的最新治理决定 ID（仅作者本人且 7 日申诉窗口内返回，"
                + "其余场景恒 null）") Long moderationDecisionId,

        @Schema(description = "乐观锁版本（v2 生命周期操作 CAS 校验用）") Long version
) {
    /** 兼容旧调用（linkedProductId=null：未关联商品） */
    public WishVO(Long id, String title, String description, java.util.List<String> mediaUrls,
                  Long categoryId, String categoryName, java.util.List<String> tags,
                  WishVisibility visibility, WishStatus status, AuditStatus auditStatus,
                  FruitType fruitType, Long authorId, String authorNickname, String authorAvatar,
                  Integer lightCount, Integer sameWishCount, Integer blessCount, Integer anonStarCount,
                  Integer supportCount, Integer commentCount, LocalDateTime expectedAt,
                  Boolean enableAiReply, LocalDateTime createdAt, LocalDateTime updatedAt,
                  java.util.List<WishGrowthRecordVO> growthRecords, Integer checkinDays,
                  WishProgressVO progress, Long moderationDecisionId, Long version) {
        this(id, title, description, mediaUrls, categoryId, null, categoryName, tags, visibility,
                status, auditStatus, fruitType, authorId, authorNickname, authorAvatar,
                lightCount, sameWishCount, blessCount, anonStarCount, supportCount, commentCount,
                expectedAt, enableAiReply, createdAt, updatedAt, growthRecords, checkinDays,
                progress, moderationDecisionId, version);
    }
}