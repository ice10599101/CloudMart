package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 宠物留言墙留言（含主人回复，两层结构：parentId 为空 = 一级留言）。
 *
 * <p>删除采用状态位（NORMAL/HIDDEN/DELETED）而非物理删除：
 * 作者可删自己的留言，房间主人可删自家墙上的留言，管理员可隐藏（审核留痕）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wall_message")
public class PetWallMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被留言宠物 ID（房间主人） */
    private Long petId;

    /** 房间主人用户 ID */
    private Long userId;

    /** 留言者用户 ID */
    private Long authorUserId;

    /** 留言者主宠 ID（展示宠物口吻，可空） */
    private Long authorPetId;

    /** 父留言 ID（NULL = 一级留言，非空 = 主人回复） */
    private Long parentId;

    /** 留言内容（1-120 字） */
    private String content;

    /** 心情标签（可选，展示用） */
    private String mood;

    /** 状态：NORMAL/HIDDEN/DELETED */
    private String status;

    /** 点赞数 */
    private Integer likeCount;

    /** 回复数 */
    private Integer replyCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
