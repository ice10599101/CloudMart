package com.cloudmart.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 邀请绑定关系（N-3）：一人只可被邀请一次（uk_invitee），禁止自邀。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_invite_relation")
public class UserInviteRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inviterId;

    private Long inviteeId;

    /** WISH-04：奖励状态 PENDING_ELIGIBILITY/ELIGIBLE/HOLD/REJECTED_LIMIT/GRANTED */
    private String rewardStatus;

    /** WISH-04：限制/拒绝原因（后台可查） */
    private String rewardReason;

    /** WISH-04：达到资格时间 */
    private java.time.LocalDateTime eligibleAt;

    /** WISH-04：规则版本（页面展示） */
    private Integer ruleVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
