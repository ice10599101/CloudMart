package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.WishCommentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("wish_comment")
public class WishComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long wishId;

    private Long userId;

    /** 父评论 ID（顶级评论为 null，仅支持二级回复） */
    private Long parentId;

    /** 被回复用户 ID（回复时冗余存储，避免联查） */
    private Long replyToUserId;

    /** 评论内容（已 XSS 转义 + 敏感词过滤后存储） */
    private String content;

    private Integer likeCount;

    private WishCommentStatus status;

    /** 是否命中敏感词（仅标记不阻断，先发后审） */
    private Boolean sensitiveHit;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
