package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 留言墙留言项（三期）：{@code mine} 我发的、{@code owner} 我是墙主人——
 * 前端据此决定"删除"按钮（作者或墙主人都可删）。
 */
@Schema(description = "留言墙留言项")
public record PetWallMessageVO(
        @Schema(description = "留言 ID") Long id,
        @Schema(description = "被留言宠物 ID") Long petId,
        @Schema(description = "父留言 ID（回复时有值）") Long parentId,
        @Schema(description = "留言者用户 ID") Long authorUserId,
        @Schema(description = "留言者昵称") String authorNickname,
        @Schema(description = "留言者宠物 ID") Long authorPetId,
        @Schema(description = "留言者宠物名") String authorPetName,
        @Schema(description = "留言者宠物种类") String authorPetSpecies,
        @Schema(description = "内容") String content,
        @Schema(description = "心情标签") String mood,
        @Schema(description = "状态: NORMAL/HIDDEN/DELETED") String status,
        @Schema(description = "点赞数") Integer likeCount,
        @Schema(description = "回复数") Integer replyCount,
        @Schema(description = "我是否点过赞") Boolean liked,
        @Schema(description = "是否我发的") Boolean mine,
        @Schema(description = "我是否是墙主人") Boolean owner,
        @Schema(description = "是否主人回复") Boolean ownerReply,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "主人回复列表") List<PetWallMessageVO> replies
) {
}
