package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 软件域审核与状态流转日志（方案 6.10）：只追加不覆盖历史原因；
 * 不记录下载 URL/密码。
 */
@Getter
@Setter
@TableName("software_audit_log")
public class SoftwareAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long revisionId;

    private Long actorId;

    /** USER/ADMIN/SYSTEM */
    private String actorType;

    private String action;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private String requestId;

    private LocalDateTime createdAt;
}
