package com.cloudmart.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知群发批次任务（ADMIN-03）：群发以批次落库，返回 jobId 与计数；
 * request_id 幂等，重试只补失败项。
 */
@Data
@TableName("notification_broadcast_job")
public class NotificationBroadcastJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号（BRD 前缀），展示与重试主键 */
    private String jobNo;

    /** 提交幂等键（客户端生成，重复提交返回原批次） */
    private String requestId;

    private String type;

    private String title;

    private String content;

    private Integer targetCount;

    private Integer deliveredCount;

    private Integer failedCount;

    private Integer skippedCount;

    /** RUNNING / COMPLETED / PARTIAL / FAILED */
    private String status;

    /** 操作管理员（X-User-Id） */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
