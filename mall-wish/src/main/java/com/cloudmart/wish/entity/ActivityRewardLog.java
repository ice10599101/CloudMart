package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ActivityRewardType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动奖励发放日志（Sprint 3.5）：uk(activity,user,reward_type) 幂等。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_activity_reward_log")
public class ActivityRewardLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动 ID */
    private Long activityId;

    /** 获奖励用户 */
    private Long userId;

    /** 奖励类型 */
    private ActivityRewardType rewardType;

    /** 数量（星光数；徽章恒 1） */
    private Integer amount;

    /** 关联 ID（徽章 ID） */
    private Long refId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
