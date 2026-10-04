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

/** 全账号注销编排任务（B20）：统一由 mall-user 编排，各服务幂等擦除后才最终成功。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_account_deletion_task")
public class AccountDeletionTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** PENDING等待期 / PRECHECK预检 / EXECUTING执行中 / COMPLETED全域完成 / BLOCKED阻断可重试 / CANCELED已撤销 / EXECUTED旧语义(需补核查) */
    private String status;

    private String reason;

    private String serviceProgress;

    /** 阻断原因（T06：OPEN_ORDERS/ERASURE_DOMAIN_NOT_WIRED 等，脱敏展示） */
    private String blockReason;

    private LocalDateTime requestedAt;

    private LocalDateTime executeAfter;

    private LocalDateTime executedAt;

    private LocalDateTime canceledAt;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
