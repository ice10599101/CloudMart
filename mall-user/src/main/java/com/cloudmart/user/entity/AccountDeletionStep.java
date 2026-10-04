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

/**
 * 注销步骤台账（T06）：每个跨域步骤一条事实——状态、尝试次数、退避重试时间、
 * 租约与最近错误。任务完成语义 = 所有必需步骤 SUCCESS；任何步骤 FAILED/BLOCKED
 * 时任务停留可恢复状态，重启/重试从未完成步骤继续。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_account_deletion_step")
public class AccountDeletionStep {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_BLOCKED = "BLOCKED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    /** 域：USER/AUTH/WISH/ORDER/COMMUNITY/NOTIFICATION/FILE/PET */
    private String domain;

    /** 步骤：SESSION_REVOKE/OPEN_ORDER_CHECK/ERASE/ANONYMIZE */
    private String step;

    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private String lastError;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
