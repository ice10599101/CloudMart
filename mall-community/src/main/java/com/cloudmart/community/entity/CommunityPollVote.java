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
 * 投票记录实体（V10 迁移，编辑器附件）。
 *
 * <p>uk(poll_id, user_id, option_id) 防重复投票；单选时业务层校验只投 1 项。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("community_poll_votes")
public class CommunityPollVote {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 投票 ID */
    private String pollId;

    /** 选项 ID */
    private Long optionId;

    /** 投票用户 ID */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
