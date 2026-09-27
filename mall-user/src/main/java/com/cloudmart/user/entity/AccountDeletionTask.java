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

    /** PENDING / EXECUTING / EXECUTED / FAILED / CANCELED */
    private String status;

    private String reason;

    private String serviceProgress;

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
