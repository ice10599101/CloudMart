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
 * 投票选项实体（V10 迁移，编辑器附件）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_poll_options")
public class CommunityPollOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 投票 ID */
    private String pollId;

    /** 选项文本 */
    private String content;

    /** 排序（越小越靠前） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
