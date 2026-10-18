package com.cloudmart.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 邀请奖励周期额度（T15）：按邀请人 + 周期（日/月）原子预占，
 * 唯一键 (inviter_id, quota_type, period) 兜底并发。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_invite_quota")
public class UserInviteQuota {

    public static final String TYPE_DAILY = "DAILY";
    public static final String TYPE_MONTHLY = "MONTHLY";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inviterId;

    private String quotaType;

    private String period;

    private Integer usedCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
