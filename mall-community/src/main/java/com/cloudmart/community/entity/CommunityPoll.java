package com.cloudmart.community.entity;

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
 * 投票主表实体（V10 迁移，编辑器附件）。
 *
 * <p>主键为客户端生成的 UUID：发布时前端幂等落库，正文无需回写 ID。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_polls")
public class CommunityPoll {

    /** 投票 ID（客户端生成 UUID） */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 宿主内容类型: POST/WISH/CAPSULE/LETTER */
    private String targetType;

    /** 宿主内容 ID */
    private String targetId;

    /** 创建者用户 ID */
    private Long creatorId;

    /** 投票问题 */
    private String question;

    /** 是否多选 */
    private Boolean isMultiple;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
