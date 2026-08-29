package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ActivityParticipantStatus;
import com.cloudmart.wish.enums.ActivityRewardType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动参与/合伙人申请记录（Sprint 3.5，文档 1.2 ㉝）。
 *
 * <p>uk(activity,user) 防重复；普通参与 JOINED；合伙人申请 PENDING →
 * APPROVED（进组）/REJECTED。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_activity_participant")
public class ActivityParticipant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动 ID */
    private Long activityId;

    /** 参与用户 */
    private Long userId;

    /** 状态 */
    private ActivityParticipantStatus status;

    /** 角色：LEADER（招募作者）/ MEMBER */
    private String role;

    /** 协作心愿 ID（合伙人申请提交） */
    private Long wishId;

    /** 技能标签 JSON */
    private String skills;

    /** 技能匹配度（0-100） */
    private Integer matchScore;

    /** 申请时间（UTC） */
    private LocalDateTime appliedAt;

    /** 审批时间（UTC） */
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
