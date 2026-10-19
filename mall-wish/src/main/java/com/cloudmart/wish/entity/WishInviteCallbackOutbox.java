package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T15：邀请资格回调 outbox——签到事务内落库，提交即送/失败退避补发。
 * 语义：只保留同用户最新一条 PENDING（新入队把旧 PENDING 置 SUPERSEDED），
 * 回调内容是"累计签到天数"快照，旧状态被新状态覆盖不会丢信息。
 */
@Data
@TableName("wish_invite_callback_outbox")
public class WishInviteCallbackOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_FAILED = "FAILED";

    /** 最大补发次数（12 次指数退避约覆盖 24h+；到顶 FAILED 留痕人工介入） */
    public static final int MAX_ATTEMPTS = 12;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer distinctSigninDays;

    private String status;

    private Integer attempts;

    private String lastError;

    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime settledAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
