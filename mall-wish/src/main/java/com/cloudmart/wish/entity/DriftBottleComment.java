package com.cloudmart.wish.entity;

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
 * 漂流瓶评论（瓶下评论树）：仅投瓶人与捞起人可评论/可见。
 *
 * <p>评论默认匿名（isAnonymous=true 对外隐藏身份），可切换实名；
 * parentId 指向被回复评论（顶级评论为 null），replyToUserId 由服务端从父评论推导。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_drift_bottle_comment")
public class DriftBottleComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 漂流瓶 ID */
    private Long bottleId;

    /** 评论者用户 ID（投瓶人或捞起人） */
    private Long userId;

    /** 父评论 ID（回复场景；顶级评论为 null） */
    private Long parentId;

    /** 被回复人用户 ID（顶级评论为 null；由服务端从父评论推导） */
    private Long replyToUserId;

    /** 评论内容（纯文本，最长 500 字） */
    private String content;

    /** 评论是否匿名（true 匿名 / false 实名） */
    private Boolean isAnonymous;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}